/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.hk2;

/**
 * A Binding represents a single registered entry in the HK2 {@link Services}
 * registry that provides a facility to get to contextual provider instances.
 * 
 * <p/>
 * Bindings represent something that is registered in {@link Services} whereas
 * a {@link Provider} provides the runtime services for the given registered
 * Binding entry in the correct context appropriate for the caller.
 *  
 * @author Jerome Dochez, Jeff Trent, Mason Taube
 */
public interface Binding<T> {

    /**
     * The {@link Descriptor} fully characterizes the attributes
     * of this Provider.
     * 
     * @return 
     *  a non-null Descriptor describing the complete set of
     *  attributes of the provider.
     */
    Descriptor getDescriptor();
  
    /**
     * Return a {@link Provider} optionally providing contextual
     * information for how the component provider will be used (e.g., for
     * contextual injection, etc).
     * 
     * <p/>
     * The return value may be unique based on the provided contextual
     * information. Caller's should therefore not assume identity equality
     * in the return value.
     *
     * @param ctx
     *      contextual information for how the provider will be used, or null
     * 
     * @return a non-null component provider instance
     */
    Provider<T> getProvider(Context ctx);

    /**
     * Return a {@link Provider} with providing contextual information
     * on how the provider will be used. Equivalent to calling 
     * <i>getProvider(null)</i>.
     * 
     * @return a non-null component provider instance
     */
    Provider<T> getProvider();
    
}
