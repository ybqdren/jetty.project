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

package org.eclipse.jetty12.server;

import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty12.server.session.Session;

/**
 * 
 *
 */
public interface SessionManager extends LifeCycle
{
    Session getSession(String id) throws Exception;
    
    void invalidate(String id) throws Exception;
    
    boolean isIdInUse(String id) throws Exception;
    
    void renewSessionId(String oldId, String oldExtendedId, String newId, String newExtendedId) throws Exception;
    
    SessionIdManager getSessionIdManager();
   
    void callSessionCreatedListeners(Session session);
    
    void callSessionDestroyedListeners(Session session);
    
    void callSessionAttributeListeners(Session session, String name, Object old, Object value);
    
    void callUnboundBindingListener(Session session, String name, Object value);
    
    void callBoundBindingListener(Session session, String name, Object value);
    
    void callSessionActivationListener(Session session, String name, Object value);
    
    void callSessionPassivationListener(Session session, String name, Object value);

    void scavenge() throws Exception;
    
    long calculateInactivityTimeout(String id, long timeRemaining, long maxInactiveMs);
}
