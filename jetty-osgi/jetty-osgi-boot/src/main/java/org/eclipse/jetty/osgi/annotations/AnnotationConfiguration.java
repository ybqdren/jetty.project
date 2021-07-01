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

package org.eclipse.jetty.osgi.annotations;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletContainerInitializer;

import org.eclipse.jetty.annotations.AnnotationParser.Handler;
import org.eclipse.jetty.osgi.boot.OSGiMetaInfConfiguration;
import org.eclipse.jetty.osgi.boot.OSGiWebappConstants;
import org.eclipse.jetty.osgi.boot.utils.BundleFileLocatorHelperFactory;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.statistic.CounterStatistic;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extend the AnnotationConfiguration to support OSGi:
 * Look for annotations inside WEB-INF/lib and also in the fragments and required bundles.
 * Discover them using a scanner adapted to OSGi instead of the jarscanner.
 */
public class AnnotationConfiguration extends org.eclipse.jetty.annotations.AnnotationConfiguration
{
    private static final Logger LOG = LoggerFactory.getLogger(org.eclipse.jetty.annotations.AnnotationConfiguration.class);

    public class BundleParserTask extends ParserTask
    {
        private Bundle _bundle;
        private Set<URL> _skipClasses;
        
        public BundleParserTask(AnnotationParser parser, Set<? extends Handler> handlers, Bundle bundle, Resource resource)
        {
            this(parser, handlers, bundle, resource, null);
        }
        
        public BundleParserTask(AnnotationParser parser, Set<? extends Handler> handlers, Bundle bundle, Resource resource, Set<URL> skipClasses)
        {
            super(parser, handlers, resource);
            _bundle = bundle;
            _skipClasses = skipClasses;
        }

        @Override
        public Void call() throws Exception
        {
            if (_parser != null)
            {
                org.eclipse.jetty.osgi.annotations.AnnotationParser osgiAnnotationParser = (org.eclipse.jetty.osgi.annotations.AnnotationParser)_parser;
                if (_stat != null)
                    _stat.start();
                osgiAnnotationParser.parse(_handlers, _bundle, _skipClasses);
                if (_stat != null)
                    _stat.end();
            }
            return null;
        }
    }

    public AnnotationConfiguration()
    {
    }

    @Override
    public Class<? extends Configuration> replaces()
    {
        return org.eclipse.jetty.annotations.AnnotationConfiguration.class;
    }

    /**
     * This parser scans the bundles using the OSGi APIs instead of assuming a jar.
     */
    @Override
    protected org.eclipse.jetty.annotations.AnnotationParser createAnnotationParser(int javaTargetVersion)
    {
        return new AnnotationParser(javaTargetVersion);
    }

    @Override
    public Resource getJarFor(ServletContainerInitializer service) throws MalformedURLException, IOException
    {
        Resource resource = super.getJarFor(service);
        // TODO This is not correct, but implemented like this to be bug for bug compatible
        // with previous implementation that could only handle actual jars and not bundles.
        if (resource != null && !resource.toString().endsWith(".jar"))
            return null;
        return resource;
    }

    /**
     * Parse the jars that are inside the webbundle's WEB-INF/lib, as well as any Require-Bundles and 
     * OSGi fragments that are stapled to the webbundle.
     * 
     * We need to be careful when parsing the webbundle and the OSGi fragment to ensure that the same
     * classes aren't scanned twice, because OSGi treats the resources of the fragment bundle as if they
     * were contained inside the webbundle.
     */
    @Override
    public void parseWebInfLib(WebAppContext context, org.eclipse.jetty.annotations.AnnotationParser parser)
        throws Exception
    {
        AnnotationParser oparser = (AnnotationParser)parser;

        if (_webInfLibStats == null)
            _webInfLibStats = new CounterStatistic();

        Bundle webbundle = (Bundle)context.getAttribute(OSGiWebappConstants.JETTY_OSGI_BUNDLE);
        
        @SuppressWarnings("unchecked")
        Map<Bundle, Resource> fragAndRequiredBundles = (Map<Bundle, Resource>)context.getAttribute(OSGiMetaInfConfiguration.FRAGMENT_AND_REQUIRED_BUNDLES_MAP);
        oparser.indexBundles(fragAndRequiredBundles);
        
        //Remember all the classes from OSGi fragment bundles so we can avoid
        //parsing them twice.
        HashSet<URL> fragmentClasses = new HashSet<>();
        if (fragAndRequiredBundles != null)
        {
            for (Map.Entry<Bundle, Resource> entry : fragAndRequiredBundles.entrySet())
            {
                //Skip any that have been uninstalled since discovery
                if (entry.getKey().getState() == Bundle.UNINSTALLED)
                    continue;

                if (StringUtil.isNotBlank(entry.getKey().getHeaders().get(Constants.FRAGMENT_HOST)))
                {
                    Enumeration<URL> classes = entry.getKey().findEntries("/", "*.class", true);
                    while (classes.hasMoreElements())
                    {
                        URL u = classes.nextElement();
                        fragmentClasses.add(u);
                    }
                }
            }
        } 

        File f = BundleFileLocatorHelperFactory.getFactory().getHelper().getBundleInstallLocation(webbundle);
        Resource resource = Resource.newResource(f.toURI());

        //scan ourselves, avoiding scanning the classes from the OSGi fragments
        oparser.indexBundle(webbundle, resource);
        parseBundle(context, oparser, webbundle, resource, fragmentClasses);
        _webInfLibStats.increment();
            
        //scan the WEB-INF/lib
        super.parseWebInfLib(context, parser);

    }

    @Override
    public void parseWebInfClasses(WebAppContext context, org.eclipse.jetty.annotations.AnnotationParser parser)
        throws Exception
    {
        Bundle webbundle = (Bundle)context.getAttribute(OSGiWebappConstants.JETTY_OSGI_BUNDLE);
        String bundleClasspath = (String)webbundle.getHeaders().get(Constants.BUNDLE_CLASSPATH);
        if (StringUtil.isBlank(bundleClasspath) || !bundleClasspath.contains("WEB-INF/classes"))
            super.parseWebInfClasses(context, parser);
    }

    /**
     * Parse a bundle, optionally skipping some classes.
     * 
     * @param context the WebAppContext of the webapp
     * @param parser the parser to use
     * @param bundle the bundle to parse 
     * @param resource the Resource representing the bundle
     * @param skipClasses URL of classes that should not be parsed
     * @throws Exception
     */
    protected void parseBundle(WebAppContext context, AnnotationParser parser,
                               Bundle bundle, Resource resource, Set<URL> skipClasses)
            throws Exception
    {
        Set<Handler> handlers = new HashSet<>();
        handlers.addAll(_discoverableAnnotationHandlers);
        if (_classInheritanceHandler != null)
            handlers.add(_classInheritanceHandler);
        handlers.addAll(_containerInitializerAnnotationHandlers);

        if (_parserTasks != null)
        {
            BundleParserTask task = new BundleParserTask(parser, handlers, bundle, resource, skipClasses);
            _parserTasks.add(task);
            if (LOG.isDebugEnabled())
                task.setStatistic(new TimeStatistic());
        }
    }

    protected void parseBundle(WebAppContext context, AnnotationParser parser,
                               Bundle bundle, Resource resource) throws Exception
    {
        parseBundle(context, parser, bundle, resource, null);
    }
}
