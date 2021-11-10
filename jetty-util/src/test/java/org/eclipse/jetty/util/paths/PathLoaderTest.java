//
// ========================================================================
// Copyright (c) 1995-2021 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.util.paths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jetty.toolchain.test.FS;
import org.eclipse.jetty.toolchain.test.MavenTestingUtils;
import org.eclipse.jetty.toolchain.test.jupiter.WorkDir;
import org.junit.jupiter.api.Test;

import static org.eclipse.jetty.toolchain.test.ExtraMatchers.ordered;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PathLoaderTest
{
    public WorkDir workDir;

    @Test
    public void testEmptyPathLoader() throws Exception
    {
        PathLoader pathLoader = new PathLoader();
        assertThat("pathLoader.size", pathLoader.size(), is(0));
        assertTrue(pathLoader.isEmpty());
    }

    @Test
    public void testResolveExisting() throws IOException
    {
        Path testPath = workDir.getEmptyPathDir();

        FS.touch(testPath.resolve("hello.txt"));

        PathLoader pathLoader = new PathLoader();
        pathLoader.add(testPath);

        assertThat("pathLoader.size", pathLoader.size(), is(1));

        Path discovered = pathLoader.resolveFirstExisting("hello.txt");
        assertEquals(testPath.resolve("hello.txt"), discovered);

        Path missing = pathLoader.resolveFirstExisting("missing.txt");
        assertNull(missing);
    }

    // TODO: test remove (cleanup)
    // TODO: test replace (cleanup)

    @Test
    public void testResolveAll() throws IOException
    {
        Path testPath = workDir.getEmptyPathDir();

        Path foo = testPath.resolve("foo");
        Path bar = testPath.resolve("bar");

        Files.createDirectories(foo.resolve("META-INF/services"));
        Files.createDirectories(bar.resolve("META-INF/services"));

        FS.touch(foo.resolve("META-INF/services/org.eclipse.jetty.Zed"));
        FS.touch(bar.resolve("META-INF/services/org.eclipse.jetty.Zed"));
        FS.touch(bar.resolve("META-INF/services/org.cometd.Widget"));

        PathLoader pathLoader = new PathLoader();
        pathLoader.add(foo);
        pathLoader.add(bar);

        assertThat("pathLoader.size", pathLoader.size(), is(2));

        List<Path> expected = new ArrayList<>();
        expected.add(foo.resolve("META-INF/services/org.eclipse.jetty.Zed"));
        expected.add(bar.resolve("META-INF/services/org.eclipse.jetty.Zed"));

        List<Path> services = pathLoader.resolveAll("META-INF/services/org.eclipse.jetty.Zed", Files::exists);
        assertThat(services, ordered(expected));
    }

    @Test
    public void testFindClassFiles() throws IOException
    {
        Path testPath = workDir.getEmptyPathDir();

        Path foo = testPath.resolve("foo");
        Path bar = testPath.resolve("bar");

        Files.createDirectories(foo.resolve("META-INF/services"));
        Files.createDirectories(foo.resolve("org/eclipse/jetty/demo"));
        Files.createDirectories(bar.resolve("META-INF/services"));
        Files.createDirectories(bar.resolve("org/cometd"));

        FS.touch(foo.resolve("META-INF/services/org.eclipse.jetty.Zed"));
        FS.touch(bar.resolve("META-INF/services/org.eclipse.jetty.Zed"));

        FS.touch(foo.resolve("org/eclipse/jetty/Zed.class"));
        FS.touch(foo.resolve("org/eclipse/jetty/demo/Extra.class"));
        FS.touch(bar.resolve("org/cometd/Widget.class"));

        PathLoader pathLoader = new PathLoader();
        pathLoader.add(foo);
        pathLoader.add(bar);

        assertThat("pathLoader.size", pathLoader.size(), is(2));

        List<Path> expected = new ArrayList<>();
        expected.add(foo.resolve("org/eclipse/jetty/demo/Extra.class"));
        expected.add(foo.resolve("org/eclipse/jetty/Zed.class"));
        expected.add(bar.resolve("org/cometd/Widget.class"));
        Collections.sort(expected);

        Stream<Path> classes = pathLoader.find(new ClassFilePredicate());

        List<Path> actual = classes.sorted().collect(Collectors.toList());
        assertThat(actual, ordered(expected));
    }

    @Test
    public void testFindClassFilesWithinJars() throws Exception
    {
        Path testPath = workDir.getEmptyPathDir();

        Path foo = testPath.resolve("foo");
        Path bar = testPath.resolve("bar");

        Files.createDirectories(foo.resolve("META-INF/services"));
        Files.createDirectories(foo.resolve("org/eclipse/jetty/demo"));
        Files.createDirectories(bar.resolve("META-INF/services"));
        Files.createDirectories(bar.resolve("org/cometd"));

        FS.touch(foo.resolve("META-INF/services/org.eclipse.jetty.Zed"));
        FS.touch(bar.resolve("META-INF/services/org.eclipse.jetty.Zed"));

        FS.touch(foo.resolve("org/eclipse/jetty/Zed.class"));
        FS.touch(foo.resolve("org/eclipse/jetty/demo/Extra.class"));
        FS.touch(bar.resolve("org/cometd/Widget.class"));

        try (PathLoader pathLoader = new PathLoader())
        {
            pathLoader.add(foo);
            pathLoader.add(bar);

            pathLoader.add(MavenTestingUtils.getTestResourcePathFile("example.jar"));
            pathLoader.add(MavenTestingUtils.getTestResourcePathFile("jar-file-resource.jar"));
            pathLoader.add(MavenTestingUtils.getTestResourcePathFile("test-base-resource.jar"));

            assertThat("pathLoader.size", pathLoader.size(), is(5));

            Stream<Path> classes = pathLoader.find(new ClassFilePredicate());

            List<String> actual = classes.map(Path::toString).collect(Collectors.toList());
            // actual.forEach(System.out::println);

            assertThat(actual, hasItem(foo.resolve("org/eclipse/jetty/demo/Extra.class").toString()));
            assertThat(actual, hasItem("/org/example/In10Only.class"));
            assertThat(actual, hasItem("/org/example/OnlyIn9.class"));
            assertThat(actual, hasItem("/org/example/onlyIn9/OnlyIn9.class"));
            assertThat(actual, hasItem("/org/example/InBoth.class"));
            assertThat(actual, hasItem("/org/example/InBoth$InnerBase.class"));
            assertThat(actual, hasItem("/org/example/Nowhere$NoOuter.class"));

            // make sure forbidden entries don't exist
            assertThat(actual, not(hasItem(containsString("WEB-INF"))));
            assertThat(actual, not(hasItem(containsString("META-INF"))));
        }

        Map<Path, FileSystemPool.FileSystemRefCount> fsCache = FileSystemPool.getCache();
        assertThat("Cache is empty", fsCache.size(), is(0));
    }
}
