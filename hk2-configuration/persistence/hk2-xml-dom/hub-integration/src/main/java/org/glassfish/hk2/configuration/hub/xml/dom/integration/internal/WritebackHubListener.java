/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.hk2.configuration.hub.xml.dom.integration.internal;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.glassfish.hk2.configuration.hub.api.BeanDatabase;
import org.glassfish.hk2.configuration.hub.api.BeanDatabaseUpdateListener;
import org.glassfish.hk2.configuration.hub.api.Change;
import org.glassfish.hk2.configuration.hub.api.Change.ChangeCategory;
import org.glassfish.hk2.configuration.hub.api.Instance;
import org.glassfish.hk2.configuration.hub.api.Type;
import org.glassfish.hk2.configuration.hub.xml.dom.integration.XmlDomIntegrationCommitMessage;
import org.glassfish.hk2.configuration.hub.xml.dom.integration.XmlDomIntegrationUtilities;
import org.glassfish.hk2.utilities.general.GeneralUtilities;
import org.glassfish.hk2.utilities.reflection.BeanReflectionHelper;
import org.glassfish.hk2.utilities.reflection.Logger;
import org.jvnet.hk2.config.ConfigBean;
import org.jvnet.hk2.config.ConfigBeanProxy;
import org.jvnet.hk2.config.ConfigModel;
import org.jvnet.hk2.config.ConfigModel.Node;
import org.jvnet.hk2.config.ConfigModel.Property;
import org.jvnet.hk2.config.ConfigSupport;
import org.jvnet.hk2.config.Dom;
import org.jvnet.hk2.config.SingleConfigCode;
import org.jvnet.hk2.config.TransactionFailure;
import org.jvnet.hk2.config.api.ConfigurationUtilities;
import org.jvnet.hk2.config.types.PropertyBag;

/**
 * Listens for updates
 * @author jwells
 */
@Singleton
public class WritebackHubListener implements BeanDatabaseUpdateListener {
    private final static String SET_PREFIX = "set";
    private final static String GET_PREFIX = "get";
    /* package */ final static String STAR = "*";
    
    private final static String PROPERTY_NAME_FIELD = "name";
    private final static String PROPERTY_VALUE_FIELD = "value";
    private final static String PROPERTY_PROPERTY = "property";
    
    private final static Map<Class<?>, Class<?>> SCALAR_MAP = new HashMap<Class<?>, Class<?>>();
    
    static {
        SCALAR_MAP.put(Boolean.class, boolean.class);
        SCALAR_MAP.put(Character.class, char.class);
        SCALAR_MAP.put(Byte.class, byte.class);
        SCALAR_MAP.put(Short.class, short.class);
        SCALAR_MAP.put(Integer.class, int.class);
        SCALAR_MAP.put(Long.class, long.class);
        SCALAR_MAP.put(Float.class, float.class);
        SCALAR_MAP.put(Double.class, double.class);
    }
    
    private final static int BEFORE = -1;
    private final static int AFTER = 1;
    
    private final static Comparator<Change> CHANGE_COMPARATOR = new Comparator<Change>() {

        @Override
        public int compare(Change o1, Change o2) {
            ChangeCategory category1 = o1.getChangeCategory();
            ChangeCategory category2 = o2.getChangeCategory();
            
            if (!category1.equals(category2)) {
                switch(category1) {
                case ADD_INSTANCE:
                    switch(category2) {
                    case REMOVE_INSTANCE:
                        return AFTER;
                    case MODIFY_INSTANCE:
                        return AFTER;
                    default:
                        return 0;
                    }
                case REMOVE_INSTANCE:
                    switch(category2) {
                    case ADD_INSTANCE:
                        return BEFORE;
                    case MODIFY_INSTANCE:
                        return AFTER;
                    default:
                        return 0;
                    }
                default:  // MODIFY_INSTANCE
                    switch(category2) {
                    case ADD_INSTANCE:
                        return BEFORE;
                    case REMOVE_INSTANCE:
                        return BEFORE;
                    default:
                        return 0;
                    }
                }
            }
            
            String instance1 = o1.getInstanceKey();
            String instance2 = o2.getInstanceKey();
            
            String type1 = o1.getChangeType().getName();
            String type2 = o2.getChangeType().getName();
            
            // Categories are the same
            if (category1.equals(ChangeCategory.ADD_INSTANCE) ||
                category1.equals(ChangeCategory.MODIFY_INSTANCE)) {
                int retVal = type1.compareTo(type2);
                if (retVal != 0) return retVal;
                
                return instance1.compareTo(instance2);
            }
            
            // Remove case is backwards
            int retVal = type2.compareTo(type1);
            if (retVal != 0) return retVal;
            
            return instance2.compareTo(instance1);
        }
        
    };
    
    @Inject
    private ConfigListener configListener;
    
    @Inject
    private ConfigurationUtilities hk2ConfigUtilities;
    
    private WritebackHubListener() {
    }
    
    private static Method getMethod(Class<?> clazz, String methodName, Class<?>[] setterType) {
        try {
            return clazz.getMethod(methodName, setterType);
        }
        catch (NoSuchMethodException e) {
        }
        
        return null;
    }
    
    private static Method findSetterMethod(String propName, Class<?> clazz, Class<?> setterType) {
        Class<?> setterTypes[] = new Class<?>[1];
        setterTypes[0] = setterType;
        
        char chars[] = propName.toCharArray();
        
        Method foundMethod = null;
        for (int lcv = 0; lcv < chars.length; lcv++) {
            boolean finished = false;
            String trySetterName;
            if (Character.isUpperCase(chars[lcv])) {
                // We are finished here, try it one extra time
                finished = true;
                
                trySetterName = SET_PREFIX + new String(chars);
            }
            else {
                chars[lcv] = Character.toUpperCase(chars[lcv]);
                
                trySetterName = SET_PREFIX + new String(chars);
            }
            
            foundMethod = getMethod(clazz, trySetterName, setterTypes);
            if (foundMethod != null) break;
            
            // foundMethod is null, but there may be a second chance
            Class<?> scalarType = SCALAR_MAP.get(setterType);
            if (scalarType != null) {
                Class<?> scalarTypes[] = new Class<?>[1];
                scalarTypes[0] = scalarType;
                
                foundMethod = getMethod(clazz, trySetterName, scalarTypes);
                if (foundMethod != null) break;
            }
            
            if (finished) break;
        }
        
        return foundMethod;
    }
    
    private static MethodAndElementName findChildGetterMethod(Dom parentDom, String xmlName, Class<?> childType) {
        ConfigModel model = parentDom.model;
        
        for (Method m : parentDom.getImplementationClass().getMethods()) {
            String methodName = m.getName();
            if (!methodName.startsWith(GET_PREFIX)) continue;
            
            Class<?> params[] = m.getParameterTypes();
            if (params.length != 0) continue;
            
            Class<?> retType = m.getReturnType();
            
            boolean single;
            if (retType == null) continue;
            
            if (childType.equals(retType)) {
                single = true;
            }
            else if (List.class.equals(retType)) {
                java.lang.reflect.Type pTypeRaw = m.getGenericReturnType();
                if (!(pTypeRaw instanceof ParameterizedType)) {
                    // To work with our stuff your lists must be parameterized
                    continue;
                }
                
                ParameterizedType pType = (ParameterizedType) pTypeRaw;
                if (pType.getActualTypeArguments().length != 1) continue;
                
                if (!(pType.getActualTypeArguments()[0] instanceof Class)) continue;
                if (!childType.equals((Class<?>) pType.getActualTypeArguments()[0])) continue;
                
                single = false;
            }
            else {
                continue;
            }
            
            ConfigModel.Property prop = model.toProperty(m);
            if (prop == null) continue;
            
            String methodXmlName = prop.xmlName();
            String elementName = methodXmlName;
            boolean hasStar = false;
            if (STAR.equals(elementName)) {
                methodXmlName = childType.getSimpleName();
                methodXmlName = ConfigModel.camelCaseToXML(methodXmlName);
                hasStar = true;
            }
            
            if (!methodXmlName.equals(xmlName)) {
                continue;
            }
            
            return new MethodAndElementName(m, methodXmlName, single, hasStar);
        }
        
        return new MethodAndElementName(null, null, true, false);
    }
    
    private void doModification(Change change, ConfigBeanProxy originalConfigBean) {
        for (PropertyChangeEvent propChange : change.getModifiedProperties()) {
            String propName = propChange.getPropertyName();
            Class<?> setterType;
            
            Object newValue = propChange.getNewValue();
            Logger.getLogger().debug("WRITEBACK: Modifying property " + propName +
                    " to value " + newValue +
                    " from value " + propChange.getOldValue());
            
            if (newValue != null) {
                setterType = newValue.getClass();
            }
            else {
                newValue = propChange.getOldValue();
                if (newValue != null) {
                    setterType = newValue.getClass();
                }
                else {
                    // Going from null to null, who cares?, and should not be possible
                    continue;
                }
            }
            
            if (List.class.isAssignableFrom(setterType)) {
                // This is a child, do not handle here
                Logger.getLogger().debug("WRITEBACK: Not modifying property " + propName +
                        " because it is of type List and is therefor a child");
                
                continue;
            }
            
            Method setter = findSetterMethod(propName, originalConfigBean.getClass(), setterType);
            if (setter == null) {
                // This is not a settable attribute (or we cannot find the setter), skip it
                Logger.getLogger().debug("WRITEBACK: Not modifying property " + propName +
                        " because we could not find a setter for it, it may be write-only");
                
                continue;
            }
            
            try {
                setter.invoke(originalConfigBean, newValue);
            }
            catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            catch (InvocationTargetException e) {
                Logger.getLogger().debug(getClass().getName(), setter.getName(), e.getTargetException());
            }
            
            Logger.getLogger().debug("WRITEBACK: property " + propName +
                    " successfully modified");
        }
    }
    
    private void modifyInstance(final Change change, final Map<String, Object> beanLikeMap, final HK2ConfigBeanMetaData metadata) {
        ConfigBeanProxy originalConfigBean = metadata.getConfigBean();
        
        LinkedList<String> added = new LinkedList<String>();
        for (PropertyChangeEvent propChange : change.getModifiedProperties()) {
            configListener.addKnownChange(propChange.getPropertyName());
            added.add(propChange.getPropertyName());
        }
        try {
            ConfigSupport.apply(new SingleConfigCode<ConfigBeanProxy>() {

                @Override
                public Object run(ConfigBeanProxy param) throws PropertyVetoException,
                        TransactionFailure {
                    doModification(change, param);
                    
                    return null;
                }
                
            }, originalConfigBean);
        }
        catch (TransactionFailure e) {
            Logger.getLogger().debug(getClass().getName(), "modifyInstance", e);
        }
        finally {
            for (String remove : added) {
                configListener.removeKnownChange(remove);
            }
        }
        
        if (!(originalConfigBean instanceof PropertyBag)) return;
        
        Dom dom = Dom.unwrap(originalConfigBean);
        if (!(dom instanceof ConfigBean)) return;
        
        configListener.skip();
        try {
            ConfigBean configBean = (ConfigBean) dom;
        
            // Now we need to add, remove or modify the properties
            for (PropertyChangeEvent propChange : change.getModifiedProperties()) {
                if (!XmlDomIntegrationUtilities.PROPERTIES.equals(propChange.getPropertyName())) continue;
                if (propChange.getNewValue() == null || !(propChange.getNewValue() instanceof Properties)) continue;
                Properties newProperties = (Properties) propChange.getNewValue();
            
                // The residue are new properties that need to be added
                Set<String> residue = new HashSet<String>();
                for (Object o : newProperties.keySet()) {
                    residue.add((String) o);
                }
            
                PropertyBag bag = (PropertyBag) originalConfigBean;
            
                List<org.jvnet.hk2.config.types.Property> oldProperties = bag.getProperty();
            
                for (org.jvnet.hk2.config.types.Property oldProperty : oldProperties) {
                    if (!newProperties.containsKey(oldProperty.getName())) {
                        Dom domProp = Dom.unwrap(oldProperty);
                        if (domProp == null) continue;
                        if (!(domProp instanceof ConfigBean)) continue;
                        
                        configListener.addKnownChange(PROPERTY_PROPERTY);
                        try {
                            ConfigSupport.deleteChild(configBean, (ConfigBean) domProp);
                        }
                        catch (TransactionFailure e) {
                            Logger.getLogger().debug(getClass().getName(), "modifyInstance-deleteChild", e);
                        }
                        finally {
                            configListener.removeKnownChange(PROPERTY_PROPERTY);
                        }
                    }
                    else if (residue.remove(oldProperty.getName())) {
                        String oldValue = oldProperty.getValue();
                        final String newValue = newProperties.getProperty(oldProperty.getName());
                        
                        if (!GeneralUtilities.safeEquals(oldValue, newValue)) {
                            try {
                                ConfigSupport.apply(new SingleConfigCode<org.jvnet.hk2.config.types.Property>() {

                                    @Override
                                    public Object run(
                                            org.jvnet.hk2.config.types.Property param)
                                            throws PropertyVetoException,
                                            TransactionFailure {
                                        param.setValue(newValue);
                                        return null;
                                    }
                                    
                                }, oldProperty);
                            }
                            catch (TransactionFailure e) {
                                Logger.getLogger().debug(getClass().getName(), "modifyInstance-apply", e);
                            }
                            
                        }
                        
                    }
                
                }
            
                for (String newKey : residue) {
                    addProperty(configBean, newKey, newProperties.getProperty(newKey));
                }
            }
        }
        finally {
            configListener.unskip();
        }
    }
    
    private static String getElementName(String typeName) {
        int index = typeName.lastIndexOf('/');
        if (index < 0) return typeName;
        
        return typeName.substring(index + 1);
    }
    
    private static String getInstanceKey(String instanceName) {
        int index = instanceName.lastIndexOf('.');
        if (index < 0) return instanceName;
        
        return instanceName.substring(index + 1);
    }
    
    private static Instance findParent(BeanDatabase inDb, String childTypeName, String childInstanceName) {
        int index = childTypeName.lastIndexOf('/');
        if (index < 0) return null;
        
        String parentTypeName = childTypeName.substring(0, index);
        
        index = childInstanceName.lastIndexOf('.');
        if (index < 0) return null;
        
        String parentInstanceName = childInstanceName.substring(0, index);
        
        Instance retVal = inDb.getInstance(parentTypeName, parentInstanceName);
        return retVal;
    }
    
    private static Map<String, String> stringify(Map<String, Object> convertMe, Map<String, String> keyTranslator) {
        HashMap<String, String> retVal = new HashMap<String, String>();
        
        for (Map.Entry<String, Object> entry : convertMe.entrySet()) {
            String key = entry.getKey();
            Object rawValue = entry.getValue();
            
            String value;
            if (rawValue == null) {
                value = null;
            }
            else {
                value = rawValue.toString();
            }
            
            String translatedKey = keyTranslator.get(key);
            if (translatedKey == null) continue;
            retVal.put(translatedKey, value);
        }
        
        return retVal;
    }
    
    private static Map<String, String> getBeanToXmlMapping(Class<?> childClass, ConfigModel childModel) {
        HashMap<String, String> retVal = new HashMap<String, String>();
        
        for (Method method : childClass.getMethods()) {
            String beanName = BeanReflectionHelper.getBeanPropertyNameFromGetter(method);
            if (beanName == null) continue;
            
            Property property = childModel.toProperty(method);
            if (property == null) continue;
            
            String xmlName = property.xmlName;
            
            retVal.put(beanName, xmlName);
        }
        
        return retVal;
    }
    
    private void addProperty(ConfigBean parent, String key, String value) {
        
        configListener.addKnownChange(PROPERTY_PROPERTY);
        try {
            HashMap<String, String> propertyTypeValues = new HashMap<String, String>();
            propertyTypeValues.put(PROPERTY_NAME_FIELD, key);
            propertyTypeValues.put(PROPERTY_VALUE_FIELD, value);
    
            try {
                ConfigSupport.createAndSet(parent, org.jvnet.hk2.config.types.Property.class, propertyTypeValues);
            }
            catch (TransactionFailure e) {
                Logger.getLogger().debug("WRITEBACK: Could not add property with key " +
                        key + " and value=" + value);
            }
        }
        finally {
            configListener.removeKnownChange(PROPERTY_PROPERTY);
        }
        
    }
    
    @SuppressWarnings("unchecked")
    private void addChild(BeanDatabase proposedDb, final Change change, final Map<String, Object> newGuy) {
        Instance instance = change.getInstanceValue();
        String instanceName = change.getInstanceKey();
        Type instanceType = change.getChangeType();
        String typeName = instanceType.getName();
        
        Logger.getLogger().debug("WRITEBACK: adding child with type " + typeName + " and instance " + instanceName);
        
        Instance parent = findParent(proposedDb, typeName, instanceName);
        if (parent == null) {
            throw new RuntimeException("WRITEBACK: could not find parent of type " + typeName + " and instance " + instanceName);
        }
        
        Object rawParentMetadata = parent.getMetadata();
        if (rawParentMetadata == null || !(rawParentMetadata instanceof HK2ConfigBeanMetaData)) {
            throw new RuntimeException("WRITEBACK: No metadata for type " + typeName + " and instance " + instanceName);
        }
        HK2ConfigBeanMetaData parentMetadata = (HK2ConfigBeanMetaData) rawParentMetadata;
        
        ConfigBeanProxy parentConfigBeanProxy = parentMetadata.getConfigBean();
        if (parentConfigBeanProxy == null) {
            throw new RuntimeException("WRITEBACK: No configuration bean for type " + typeName + " and instance " + instanceName);
        }
        
        final Dom parentDom = Dom.unwrap(parentConfigBeanProxy);
        if (parentDom == null) {
            throw new RuntimeException("WRITEBACK: No parent Dom for type " + typeName + " and instance " + instanceName);
        }
        
        ConfigModel childModel;
        Class<? extends ConfigBeanProxy> childClass;
        
        final String childElementName = getElementName(typeName);
        Dom childDom = parentDom.element(childElementName);
        
        if (childDom == null) {
            Property property = parentDom.model.getElement(childElementName);
            if (property == null) {
                Property starProp = parentDom.model.getElement(STAR);
                if (starProp != null) {
                    property = starProp;
                }
                else {
                    throw new RuntimeException("WRITEBACK: No available child Dom for type " + typeName + " and instance " + instanceName +
                        " with element name " + childElementName + ".  Available names=" + parentDom.model.getElementNames());
                }
            }
            
            if (!(property instanceof Node)) {
                throw new RuntimeException("WRITEBACK: No child Dom for type " + typeName + " and instance " + instanceName +
                        " with element name " + childElementName + " is not a Node type");
            }
            
            Node node = (Node) property;
            childModel = node.getModel();
            
            childClass = childModel.getProxyType();
        }
        else {
            childModel = childDom.model;
            childClass = (Class<? extends ConfigBeanProxy>) childDom.getImplementationClass();
        }
        
        final Class<? extends ConfigBeanProxy> fChildClass = childClass;
        
        final Map<String, String> beanXmlMapping = getBeanToXmlMapping(fChildClass, childModel);
        
        MethodAndElementName maen = findChildGetterMethod(parentDom, childElementName, fChildClass);
        
        if (maen.method == null) {
            throw new RuntimeException("WRITEBACK: Could not find getter for " + typeName + " and instance " + instanceName +
                    " with element name " + childElementName + " and child class " + fChildClass.getName() +
                    ".  This can sometimes happen if a bean has two fields marked @Element(\"*\")");
        }
        
        configListener.addKnownChange(maen.elementName);
        if (maen.hasStar) {
            configListener.addKnownChange(STAR);
        }
        ConfigBeanProxy child = null;
        configListener.skip();
        try {
            if (parentDom instanceof ConfigBean) {
                try {
                    final Object propsRaw = newGuy.get(XmlDomIntegrationUtilities.PROPERTIES);
                    
                    child = (ConfigBeanProxy) ConfigSupport.apply(new SingleConfigCode<ConfigBeanProxy>() {

                        @Override
                        public Object run(ConfigBeanProxy param)
                                throws PropertyVetoException,
                                TransactionFailure {
                            ConfigBeanProxy childRetVal = (ConfigBeanProxy) hk2ConfigUtilities.addChildWithAttributes(param,
                                    (ConfigBean) parentDom,
                                    fChildClass,
                                    ConfigSupport.convertMapToAttributeChanges(stringify(newGuy, beanXmlMapping)),
                                    null);
                            
                            ConfigBean childMasterView = (ConfigBean) Dom.unwrap(childRetVal);
                            
                            if ((propsRaw != null) &&
                                (propsRaw instanceof Properties)) {
                            
                                Properties props = (Properties) propsRaw;
                                for (Map.Entry<Object, Object> entry : props.entrySet()) {
                                    String key = (String) entry.getKey();
                                    String value = (String) entry.getValue();
                                
                                    Map<String, String> propertyValues = new HashMap<String, String>();
                                    propertyValues.put(PROPERTY_NAME_FIELD, key);
                                    propertyValues.put(PROPERTY_VALUE_FIELD, value);
                                
                                    hk2ConfigUtilities.addChildWithAttributes(childRetVal,
                                        childMasterView,
                                        org.jvnet.hk2.config.types.Property.class,
                                        ConfigSupport.convertMapToAttributeChanges(propertyValues),
                                        null);
                                }
                            }
                            
                            return childRetVal;
                        }
                        
                    }, parentConfigBeanProxy);
                    
                    
                }
                catch (TransactionFailure e) {
                    throw new RuntimeException(e);
                }
            }
            else {
                Logger.getLogger().debug("WRITEBACK: The parent bean is not a ConfigBean, no addition is possible");
            }
        }
        finally {
            configListener.unskip();
            if (maen.hasStar) {
                configListener.removeKnownChange(STAR);
            }
            configListener.removeKnownChange(maen.elementName);
        }
        
        if (child != null) {
            Logger.getLogger().debug("WRITEBACK: added child of type " + typeName + " and instance " + instanceName);
            instance.setMetadata(new HK2ConfigBeanMetaData(child));
        }
        else {
            throw new RuntimeException("WRITEBACK: failed to add child of type " + typeName + " and instance " + instanceName);
        }
    }
    
    @SuppressWarnings("unchecked")
    private void removeChild(final Change change) {
        String instanceName = change.getInstanceKey();
        Type instanceType = change.getChangeType();
        String typeName = instanceType.getName();
        
        Logger.getLogger().debug("WRITEBACK: removing type " + typeName + " of instance " + instanceName);
        
        Dom parentDom = null;
        ConfigBeanProxy parentConfigBeanProxy = null;
        
        Object childMetadataRaw = change.getInstanceValue().getMetadata();
        if (childMetadataRaw == null || !(childMetadataRaw instanceof HK2ConfigBeanMetaData)) {
            Logger.getLogger().debug("WRITEBACK: during child removal could not find metadata of type " + typeName + " of instance " + instanceName);
            return;
                
        }
        HK2ConfigBeanMetaData childMetadata = (HK2ConfigBeanMetaData) childMetadataRaw;
            
        ConfigBeanProxy childProxy = childMetadata.getConfigBean();
        Dom childDom = Dom.unwrap(childProxy);
        if (childDom == null) {
            Logger.getLogger().debug("WRITEBACK: during removal could not find parent of type " + typeName + " of instance " + instanceName);
            return;
        }
            
        parentDom = childDom.parent();
        if (parentDom != null) {
            parentConfigBeanProxy = parentDom.createProxy();
        }
        else {
            // This is a root node.  Just unregister it
            childDom.release();
                
            Logger.getLogger().debug("WRITEBACK: released root of type " + typeName + " and instance " + instanceName);
            return;
        }
        
        if (parentDom == null || parentConfigBeanProxy == null) {
            Logger.getLogger().debug("WRITEBACK: during removal could not find parent Dom of type " + typeName + " of instance " + instanceName);
            return;
        }
        
        final String childElementName = getElementName(typeName);
        childDom = parentDom.element(childElementName);
        if (childDom == null) {
            Logger.getLogger().debug("WRITEBACK: during removal could not find instance Dom of type " + typeName + " of instance " + instanceName);
            return;
        }
        
        MethodAndElementName maen = findChildGetterMethod(parentDom, childElementName, childDom.getImplementationClass());
        if (maen == null || maen.method == null) {
            Logger.getLogger().debug("WRITEBACK: during removal could not find proper getter to remove child " + typeName + " of instance " + instanceName);
            return;
        }
        
        if (!maen.single) {
            String instanceKey = getInstanceKey(instanceName);
            
            // Need to find the proper child to remove
            Method parentListMethod = maen.method;
            if (parentListMethod == null) {
                Logger.getLogger().debug("WRITEBACK: during removal could not find getter for element " + childElementName +
                        " of class " + childDom.getImplementationClass().getName());
                return;
            }
            
            List<ConfigBeanProxy> removeFromMe = null;
            try {
                Object result = parentListMethod.invoke(parentConfigBeanProxy);
                if (result instanceof List) {
                    removeFromMe = (List<ConfigBeanProxy>) result;
                }
            }
            catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            catch (InvocationTargetException e) {
                Logger.getLogger().debug(getClass().getName(), "removeChild", e.getTargetException());
                
                return;
            }
            
            Dom removeMeDom = null;
            for (ConfigBeanProxy candidate : removeFromMe) {
                Dom candidateDom = Dom.unwrap(candidate);
                if (candidateDom == null) continue;
                
                String candidateKey = candidateDom.getKey();
                if (candidateKey == null) continue;
                
                if (instanceKey.equals(candidateKey)) {
                    removeMeDom = candidateDom;
                    break;
                }
            }
            
            childDom = removeMeDom;
        }
        
        if ((parentDom instanceof ConfigBean) && (childDom instanceof ConfigBean)) {
            ConfigBean parentConfigBean = (ConfigBean) parentDom;
            ConfigBean childConfigBean = (ConfigBean) childDom;
            
            configListener.addKnownChange(maen.elementName);
            if (maen.hasStar) {
                configListener.addKnownChange(STAR);
            }
            try {
                ConfigSupport.deleteChild(parentConfigBean, childConfigBean);
            }
            catch (TransactionFailure e) {
                Logger.getLogger().debug(getClass().getName(), "removeChild", e);
            }
            finally {
                if (maen.hasStar) {
                    configListener.removeKnownChange(STAR);
                }
                configListener.removeKnownChange(maen.elementName);
            }
        }
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.configuration.hub.api.BeanDatabaseUpdateListener#databaseHasChanged(org.glassfish.hk2.configuration.hub.api.BeanDatabase, java.lang.Object, java.util.List)
     */
    @SuppressWarnings("unchecked")
    private void internalDatabaseHasChanged(BeanDatabase proposedDatabase,
            Object commitMessage, List<Change> changes) throws RuntimeException {
        Logger.getLogger().debug("WRITEBACK: Change in Hub detected");
        if ((commitMessage != null) &&
                (commitMessage instanceof XmlDomIntegrationCommitMessage)) {
            // This is a change coming from the bean into the map, not
            // the other way around, so stop right now
            Logger.getLogger().debug("WRITEBACK: Change ignored as hub-integration was responsible");
            
            return;
        }
        
        TreeSet<Change> sortedChanges = new TreeSet<Change>(CHANGE_COMPARATOR);
        for (Change change : changes) {
            ChangeCategory category = change.getChangeCategory();
            
            if (category.equals(ChangeCategory.ADD_INSTANCE) ||
                category.equals(ChangeCategory.MODIFY_INSTANCE) ||
                category.equals(ChangeCategory.REMOVE_INSTANCE)) {
                sortedChanges.add(change);
            }
        }
        
        for (Change change : sortedChanges) {
            Logger.getLogger().debug("WRITEBACK: Processing change: " + change);
            
            Instance instance = change.getInstanceValue();
            if (instance == null) continue;
            
            Object rawBean = instance.getBean();
            if (!(rawBean instanceof Map)) {
                // Not translated, forget it
                continue;
            }
            Map<String, Object> beanLikeMap = (Map<String,Object>) rawBean;
            
            Change.ChangeCategory category = change.getChangeCategory();
            
            if (category.equals(Change.ChangeCategory.MODIFY_INSTANCE)) {
                Object rawMetaData = instance.getMetadata();
                if (rawMetaData == null ||
                        !(rawMetaData instanceof HK2ConfigBeanMetaData)) {
                    // Not our metadata, forget it
                    continue;
                }
                HK2ConfigBeanMetaData metadata = (HK2ConfigBeanMetaData) rawMetaData;
                if (metadata.getConfigBean() == null) {
                    // We did put it in, but it was not a ConfigBeanProxy (can't happen... probably)
                    continue;
                }
                
                modifyInstance(change, beanLikeMap, metadata);
            }
            else if (category.equals(Change.ChangeCategory.ADD_INSTANCE)) {
                addChild(proposedDatabase, change, beanLikeMap);
            }
            else if (category.equals(Change.ChangeCategory.REMOVE_INSTANCE)) {
                removeChild(change);
            }
            
            // Others we just ignore 
        }
        
        Logger.getLogger().debug("WRITEBACK: All changes processed");

    }
    
    /* (non-Javadoc)
     * @see org.glassfish.hk2.configuration.hub.api.BeanDatabaseUpdateListener#commitDatabaseChange(org.glassfish.hk2.configuration.hub.api.BeanDatabase, org.glassfish.hk2.configuration.hub.api.BeanDatabase, java.lang.Object, java.util.List)
     */
    @Override
    public void prepareDatabaseChange(BeanDatabase oldDatabase,
            BeanDatabase proposedDatabase, Object commitMessage,
            List<Change> changes) {
        internalDatabaseHasChanged(proposedDatabase, commitMessage, changes);
    }
    
    @Override
    public void commitDatabaseChange(
            BeanDatabase referenceDatabase,
            BeanDatabase newDatabase,
            Object commitMessage, List<Change> changes) {
        // Do nothing
        // internalDatabaseHasChanged(newDatabase, commitMessage, changes);
    }
    
    /* (non-Javadoc)
     * @see org.glassfish.hk2.configuration.hub.api.BeanDatabaseUpdateListener#rollbackDatabaseChange(org.glassfish.hk2.configuration.hub.api.BeanDatabase, org.glassfish.hk2.configuration.hub.api.BeanDatabase, java.lang.Object, java.util.List)
     */
    @Override
    public void rollbackDatabaseChange(BeanDatabase currentDatabase,
            BeanDatabase proposedDatabase, Object commitMessage,
            List<Change> changes) {
        // Do nothing
        
    }
    
    private final static class MethodAndElementName {
        private final Method method;
        private final String elementName;  // Could be "*"
        private final boolean single;
        private final boolean hasStar;
        
        private MethodAndElementName(Method m, String e, boolean s, boolean hasStar) {
            this.method = m;
            this.elementName = e;
            this.single = s;
            this.hasStar = hasStar;
        }
        
        @Override
        public String toString() {
            return "MethodAndElementName(" + method + "," + elementName + "," + single + "," + hasStar + ")";
        }
    }

    

}
