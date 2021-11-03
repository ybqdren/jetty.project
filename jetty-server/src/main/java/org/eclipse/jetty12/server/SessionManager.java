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

import org.eclipse.jetty12.server.session.Session;

/**
 * 
 *
 */
public interface SessionManager
{
    public Session getSession(String id) throws Exception;
    
    public void invalidate(String id) throws Exception;
    
    public boolean isIdInUse(String id) throws Exception;
    
    public void renewSessionId(String oldId, String oldExtendedId, String newId, String newExtendedId) throws Exception;
    
    public void scavenge() throws Exception;
}
