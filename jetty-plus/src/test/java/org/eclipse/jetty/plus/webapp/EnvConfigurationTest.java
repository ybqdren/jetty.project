//
//  ========================================================================
//  Copyright (c) 1995-2022 Mort Bay Consulting Pty Ltd and others.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.plus.webapp;

import java.net.URL;
import java.util.Enumeration;
import javax.naming.InitialContext;
import javax.naming.Reference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class EnvConfigurationTest
{
    @AfterEach
    public void resetSystemProps()
    {
        System.setProperty("jdk.jndi.object.factoriesFilter", "");
    }

    @ParameterizedTest
    @ValueSource(strings = {"!*", ""})
    public void testEnvConfiguration(String factoriesFilterValue) throws Exception
    {
        System.out.println("jdk.jndi.object.factoriesFilter(initial) = " + System.getProperty("jdk.jndi.object.factoriesFilter"));
        System.setProperty("jdk.jndi.object.factoriesFilter", factoriesFilterValue);

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        whereIsThisComingFrom(cl, javax.naming.Reference.class);
        whereIsThisComingFrom(cl, javax.naming.Context.class);

        InitialContext ic = new InitialContext();
        Object res = ic.lookup("java:comp");
        System.out.printf("res (%s) = [%s]%n", res.getClass(), res);
        if (res instanceof Reference) {
            Reference ref = (Reference) res;
            System.out.printf("ref.className = %s%n", ref.getClassName());
            System.out.printf("ref.factoryClassLocation = %s%n", ref.getFactoryClassLocation());
            System.out.printf("ref.factoryClassName = %s%n", ref.getFactoryClassName());
        }
    }

    public static void whereIsThisComingFrom(ClassLoader cl, Class<?> clazz)
    {
        String classAsResource = clazz.getName().replace('.', '/') + ".class";
        whereIsThisComingFrom(cl, classAsResource);
    }

    public static void whereIsThisComingFrom(ClassLoader cl, String resourceName)
    {
        try
        {
            Enumeration<URL> urls = cl.getResources(resourceName);
            System.out.printf("Looking for: %s%n", resourceName);
            int count = 0;
            while (urls.hasMoreElements())
            {
                URL url = urls.nextElement();
                System.out.printf(" - Found: %s%n", url.toExternalForm());
                count++;
            }
            System.out.printf(" Found %d times%n", count);
        }
        catch (Throwable t)
        {
            System.out.printf("Whoops: cannot locate: %s%n", resourceName);
            t.printStackTrace();
        }
    }
}
