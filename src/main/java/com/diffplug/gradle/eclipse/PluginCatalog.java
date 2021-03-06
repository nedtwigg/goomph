/*
 * Copyright 2015 DiffPlug
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.diffplug.gradle.eclipse;

import java.io.File;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import org.osgi.framework.Version;

import aQute.lib.filter.Filter;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;

import com.diffplug.common.base.Errors;
import com.diffplug.common.swt.os.OS;
import com.diffplug.common.swt.os.SwtPlatform;
import com.diffplug.gradle.ZipUtil;

/** Catalogs all of the plugins and their versions in the given paths. */

public class PluginCatalog {
	/** A map from plugin name to a set of available versions. */
	private SetMultimap<String, Version> map = HashMultimap.create();
	/** A set containing plugins which are specific to platforms which we don't support. */
	private Set<String> unsupportedPlatform = Sets.newHashSet();
	/** A map from plugin name to the list of versions that are okay to resolve with the first entry. */
	private Map<String, List<Version>> resolvable = Maps.newHashMap();

	private static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";
	private static final String BUNDLE_NAME = "Bundle-SymbolicName";
	private static final String BUNDLE_VERSION = "Bundle-Version";
	private static final String ECLIPSE_PLATFORM_FILTER = "Eclipse-PlatformFilter";

	/**
	 * Catalogs all of the plugins in the given roots.  If a plugin
	 * exists with two versions, an exception is thrown.
	 */
	public PluginCatalog(List<File> roots) {
		for (File root : roots) {
			Preconditions.checkArgument(root.exists(), "Root '%s' does not exist.", root);
			File pluginRoot = root;
			File plugins = root.toPath().resolve("plugins").toFile();
			if (plugins.exists()) {
				pluginRoot = plugins;
			}

			List<File> files = Arrays.asList(pluginRoot.listFiles());
			Preconditions.checkArgument(files.size() > 0, "No plugins found in " + root);
			files.stream()
					.filter(File::isFile)
					.filter(file -> file.getName().endsWith(".jar"))
					.filter(file -> !file.getName().endsWith("_SNAPSHOT.jar"))
					.forEach(file -> {
						try {
							ZipUtil.read(file, MANIFEST_PATH, input -> {
								Manifest parsed = new Manifest(input);

								// parse out the name (looking out for the ";singleton=true" names
								String name = parsed.getMainAttributes().getValue(BUNDLE_NAME);
								int splitIdx = name.indexOf(';');
								if (splitIdx > 0) {
									name = name.substring(0, splitIdx);
								}

								// parse out the platform filter (if any)
								// if it doesn't match an OS that we support, throw it out
								String platformFilter = parsed.getMainAttributes().getValue(ECLIPSE_PLATFORM_FILTER);
								if (platformFilter != null) {
									Filter filter = new Filter(platformFilter.replace(" ", ""));
									boolean isSupportedOS = Arrays.asList(OS.values()).stream()
											.map(SwtPlatform::fromOS)
											.anyMatch(platform -> filter.match(new Hashtable<>(platform.platformProperties())));
									if (!isSupportedOS) {
										unsupportedPlatform.add(name);
										return;
									}
								}

								// parse out the version
								String versionRaw = parsed.getMainAttributes().getValue(BUNDLE_VERSION);
								Version version = Version.parseVersion(versionRaw);
								map.put(name, version);
							});
						} catch (Exception e) {
							throw Errors.asRuntime(e);
						}
					});
		}
	}

	/** If the given plugin has multiple versions, and those versions match the versions passed in, it will resolve them with the first version in this list. */
	public void resolveWithFirst(String pluginName, String... versions) {
		resolveWithFirst(pluginName, Arrays.asList(versions));
	}

	/** If the given plugin has multiple versions, and those versions match the versions passed in, it will resolve them with the first version in this list. */
	public void resolveWithFirst(String pluginName, List<String> versions) {
		resolvable.put(pluginName, versions.stream()
				.map(Version::parseVersion)
				.collect(Collectors.toList()));
	}

	/** Returns true if the given plugin is for a supported platform. */
	public boolean isSupportedPlatform(String plugin) {
		return !unsupportedPlatform.contains(plugin);
	}

	/** Returns the version for the given plugin. */
	public Version getVersionFor(String plugin) {
		Set<Version> versions = map.get(plugin);
		if (versions.size() == 1) {
			return Iterables.get(versions, 0);
		} else if (versions.isEmpty()) {
			throw new IllegalArgumentException("No such plugin: " + plugin);
		} else {
			List<Version> resolveWith = resolvable.get(plugin);
			if (resolveWith == null) {
				throw new IllegalArgumentException("Conflicting versions for '" + plugin + "'! Available versions: '" + versions + "'.");
			} else {
				if (Sets.newHashSet(resolveWith).equals(versions)) {
					return resolveWith.get(0);
				} else {
					throw new IllegalArgumentException("Conflicts don't match!  Suggested resolution was " + resolveWith + ", but available was " + versions);
				}
			}
		}
	}

	@Override
	public String toString() {
		return map.entries().stream().map(entry -> entry.toString()).collect(Collectors.joining("\n"));
	}
}
