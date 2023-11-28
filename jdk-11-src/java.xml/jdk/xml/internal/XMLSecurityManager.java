/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package jdk.xml.internal;

import com.sun.org.apache.xalan.internal.XalanConstants;
import com.sun.org.apache.xerces.internal.util.SecurityManager;
import java.util.concurrent.CopyOnWriteArrayList;
import jdk.xml.internal.SecuritySupport;
import org.xml.sax.SAXException;


/**
 * This class manages standard and implementation-specific limitations.
 *
 */
public final class XMLSecurityManager {

    /**
     * States of the settings of a property, in the order: default value, value
     * set by FEATURE_SECURE_PROCESSING, jaxp.properties file, jaxp system
     * properties, and jaxp api properties
     */
    public static enum State {
        //this order reflects the overriding order

        DEFAULT("default"), FSP("FEATURE_SECURE_PROCESSING"),
        JAXPDOTPROPERTIES("jaxp.properties"), SYSTEMPROPERTY("system property"),
        APIPROPERTY("property");

        final String literal;
        State(String literal) {
            this.literal = literal;
        }

        String literal() {
            return literal;
        }
    }

    /**
     * Limits managed by the security manager
     */
    public static enum Limit {

        ENTITY_EXPANSION_LIMIT("EntityExpansionLimit", XalanConstants.JDK_ENTITY_EXPANSION_LIMIT,
                XalanConstants.SP_ENTITY_EXPANSION_LIMIT, 0, 64000, Processor.PARSER),
        MAX_OCCUR_NODE_LIMIT("MaxOccurLimit", XalanConstants.JDK_MAX_OCCUR_LIMIT,
                XalanConstants.SP_MAX_OCCUR_LIMIT, 0, 5000, Processor.PARSER),
        ELEMENT_ATTRIBUTE_LIMIT("ElementAttributeLimit", XalanConstants.JDK_ELEMENT_ATTRIBUTE_LIMIT,
                XalanConstants.SP_ELEMENT_ATTRIBUTE_LIMIT, 0, 10000, Processor.PARSER),
        TOTAL_ENTITY_SIZE_LIMIT("TotalEntitySizeLimit", XalanConstants.JDK_TOTAL_ENTITY_SIZE_LIMIT,
                XalanConstants.SP_TOTAL_ENTITY_SIZE_LIMIT, 0, 50000000, Processor.PARSER),
        GENERAL_ENTITY_SIZE_LIMIT("MaxEntitySizeLimit", XalanConstants.JDK_GENERAL_ENTITY_SIZE_LIMIT,
                XalanConstants.SP_GENERAL_ENTITY_SIZE_LIMIT, 0, 0, Processor.PARSER),
        PARAMETER_ENTITY_SIZE_LIMIT("MaxEntitySizeLimit", XalanConstants.JDK_PARAMETER_ENTITY_SIZE_LIMIT,
                XalanConstants.SP_PARAMETER_ENTITY_SIZE_LIMIT, 0, 1000000, Processor.PARSER),
        MAX_ELEMENT_DEPTH_LIMIT("MaxElementDepthLimit", XalanConstants.JDK_MAX_ELEMENT_DEPTH,
                XalanConstants.SP_MAX_ELEMENT_DEPTH, 0, 0, Processor.PARSER),
        MAX_NAME_LIMIT("MaxXMLNameLimit", XalanConstants.JDK_XML_NAME_LIMIT,
                XalanConstants.SP_XML_NAME_LIMIT, 1000, 1000, Processor.PARSER),
        ENTITY_REPLACEMENT_LIMIT("EntityReplacementLimit", XalanConstants.JDK_ENTITY_REPLACEMENT_LIMIT,
                XalanConstants.SP_ENTITY_REPLACEMENT_LIMIT, 0, 3000000, Processor.PARSER),
        XPATH_GROUP_LIMIT("XPathGroupLimit", XalanConstants.XPATH_GROUP_LIMIT,
                XalanConstants.XPATH_GROUP_LIMIT, 10, 10, Processor.XPATH),
        XPATH_OP_LIMIT("XPathExprOpLimit", XalanConstants.XPATH_OP_LIMIT,
                XalanConstants.XPATH_OP_LIMIT, 100, 100, Processor.XPATH),
        XPATH_TOTALOP_LIMIT("XPathTotalOpLimit", XalanConstants.XPATH_TOTALOP_LIMIT,
                XalanConstants.XPATH_TOTALOP_LIMIT, 10000, 10000, Processor.XPATH)
        ;

        final String key;
        final String apiProperty;
        final String systemProperty;
        final int defaultValue;
        final int secureValue;
        final Processor processor;

        Limit(String key, String apiProperty, String systemProperty, int value,
                int secureValue, Processor processor) {
            this.key = key;
            this.apiProperty = apiProperty;
            this.systemProperty = systemProperty;
            this.defaultValue = value;
            this.secureValue = secureValue;
            this.processor = processor;
        }

        public boolean equalsAPIPropertyName(String propertyName) {
            return (propertyName == null) ? false : apiProperty.equals(propertyName);
        }

        public boolean equalsSystemPropertyName(String propertyName) {
            return (propertyName == null) ? false : systemProperty.equals(propertyName);
        }

        public String key() {
            return key;
        }

        public String apiProperty() {
            return apiProperty;
        }

        String systemProperty() {
            return systemProperty;
        }

        public int defaultValue() {
            return defaultValue;
        }

        public boolean isSupported(Processor p) {
            return processor == p;
        }

        int secureValue() {
            return secureValue;
        }
    }

    /**
     * Map old property names with the new ones
     */
    public static enum NameMap {

        ENTITY_EXPANSION_LIMIT(XalanConstants.SP_ENTITY_EXPANSION_LIMIT, XalanConstants.ENTITY_EXPANSION_LIMIT),
        MAX_OCCUR_NODE_LIMIT(XalanConstants.SP_MAX_OCCUR_LIMIT, XalanConstants.MAX_OCCUR_LIMIT),
        ELEMENT_ATTRIBUTE_LIMIT(XalanConstants.SP_ELEMENT_ATTRIBUTE_LIMIT, XalanConstants.ELEMENT_ATTRIBUTE_LIMIT);
        final String newName;
        final String oldName;

        NameMap(String newName, String oldName) {
            this.newName = newName;
            this.oldName = oldName;
        }

        String getOldName(String newName) {
            if (newName.equals(this.newName)) {
                return oldName;
            }
            return null;
        }
    }

    /**
     * Supported processors
     */
    public static enum Processor {
        PARSER,
        XPATH,
    }

    private static final int NO_LIMIT = 0;

    /**
     * Values of the properties
     */
    private final int[] values;

    /**
     * States of the settings for each property
     */
    private State[] states;

    /**
     * Flag indicating if secure processing is set
     */
    boolean secureProcessing;

    /**
     * States that determine if properties are set explicitly
     */
    private boolean[] isSet;


    /**
     * Index of the special entityCountInfo property
     */
    private final int indexEntityCountInfo = 10000;
    private String printEntityCountInfo = "";

    /**
     * Default constructor. Establishes default values for known security
     * vulnerabilities.
     */
    public XMLSecurityManager() {
        this(false);
    }

    /**
     * Instantiate Security Manager in accordance with the status of
     * secure processing
     * @param secureProcessing
     */
    public XMLSecurityManager(boolean secureProcessing) {
        values = new int[Limit.values().length];
        states = new State[Limit.values().length];
        isSet = new boolean[Limit.values().length];
        this.secureProcessing = secureProcessing;
        for (Limit limit : Limit.values()) {
            if (secureProcessing) {
                values[limit.ordinal()] = limit.secureValue;
                states[limit.ordinal()] = State.FSP;
            } else {
                values[limit.ordinal()] = limit.defaultValue();
                states[limit.ordinal()] = State.DEFAULT;
            }
        }
        //read system properties or jaxp.properties
        readSystemProperties();
    }

    /**
     * Setting FEATURE_SECURE_PROCESSING explicitly
     */
    public void setSecureProcessing(boolean secure) {
        secureProcessing = secure;
        for (Limit limit : Limit.values()) {
            if (secure) {
                setLimit(limit.ordinal(), State.FSP, limit.secureValue());
            } else {
                setLimit(limit.ordinal(), State.FSP, limit.defaultValue());
            }
        }
    }

    /**
     * Return the state of secure processing
     * @return the state of secure processing
     */
    public boolean isSecureProcessing() {
        return secureProcessing;
    }

    /**
     * Finds a limit's new name with the given property name.
     * @param propertyName the property name specified
     * @return the limit's new name if found, null otherwise
     */
    public String find(String propertyName) {
        for (Limit limit : Limit.values()) {
            if ((limit.systemProperty() != null && limit.systemProperty().equals(propertyName)) ||
                    limit.apiProperty().equals(propertyName)) {
                // current spec: new property name == systemProperty
                return limit.systemProperty();
            }
        }

        if (propertyName!= null && propertyName.equals(XalanConstants.JDK_ENTITY_COUNT_INFO) ||
                propertyName.equals("jdk.xml.getEntityCountInfo")) {
            return XalanConstants.JDK_ENTITY_COUNT_INFO;
        }

        return null;
    }

    /**
     * Set limit by property name and state
     * @param propertyName property name
     * @param state the state of the property
     * @param value the value of the property
     * @return true if the property is managed by the security manager; false
     *              if otherwise.
     */
    public boolean setLimit(String propertyName, State state, Object value) {
        int index = getIndex(propertyName);
        if (index > -1) {
            setLimit(index, state, value);
            return true;
        }
        return false;
    }

    /**
     * Set the value for a specific limit.
     *
     * @param limit the limit
     * @param state the state of the property
     * @param value the value of the property
     */
    public void setLimit(Limit limit, State state, int value) {
        setLimit(limit.ordinal(), state, value);
    }

    /**
     * Set the value of a property by its index
     *
     * @param index the index of the property
     * @param state the state of the property
     * @param value the value of the property
     */
    public void setLimit(int index, State state, Object value) {
        if (index == indexEntityCountInfo) {
            printEntityCountInfo = (String)value;
        } else {
            int temp;
            if (value instanceof Integer) {
                temp = (Integer)value;
            } else {
                temp = Integer.parseInt((String) value);
                if (temp < 0) {
                    temp = 0;
                }
            }
            setLimit(index, state, temp);
        }
    }

    /**
     * Set the value of a property by its index
     *
     * @param index the index of the property
     * @param state the state of the property
     * @param value the value of the property
     */
    public void setLimit(int index, State state, int value) {
        if (index == indexEntityCountInfo) {
            //if it's explicitly set, it's treated as yes no matter the value
            printEntityCountInfo = XalanConstants.JDK_YES;
        } else {
            //only update if it shall override
            if (state.compareTo(states[index]) >= 0) {
                values[index] = value;
                states[index] = state;
                isSet[index] = true;
            }
        }
    }

    /**
     * Return the value of the specified property
     *
     * @param propertyName the property name
     * @return the value of the property as a string. If a property is managed
     * by this manager, its value shall not be null.
     */
    public String getLimitAsString(String propertyName) {
        int index = getIndex(propertyName);
        if (index > -1) {
            return getLimitValueByIndex(index);
        }

        return null;
    }
    /**
     * Return the value of the specified property
     *
     * @param limit the property
     * @return the value of the property
     */
    public int getLimit(Limit limit) {
        return values[limit.ordinal()];
    }

    /**
     * Return the value of a property by its ordinal
     *
     * @param limit the property
     * @return value of a property
     */
    public String getLimitValueAsString(Limit limit) {
        return Integer.toString(values[limit.ordinal()]);
    }

    /**
     * Return the value of a property by its ordinal
     *
     * @param index the index of a property
     * @return limit of a property as a string
     */
    public String getLimitValueByIndex(int index) {
        if (index == indexEntityCountInfo) {
            return printEntityCountInfo;
        }

        return Integer.toString(values[index]);
    }

    /**
     * Return the state of the limit property
     *
     * @param limit the limit
     * @return the state of the limit property
     */
    public State getState(Limit limit) {
        return states[limit.ordinal()];
    }

    /**
     * Return the state of the limit property
     *
     * @param limit the limit
     * @return the state of the limit property
     */
    public String getStateLiteral(Limit limit) {
        return states[limit.ordinal()].literal();
    }

    /**
     * Get the index by property name
     *
     * @param propertyName property name
     * @return the index of the property if found; return -1 if not
     */
    public int getIndex(String propertyName) {
        for (Limit limit : Limit.values()) {
            if (limit.equalsAPIPropertyName(propertyName)) {
                //internally, ordinal is used as index
                return limit.ordinal();
            }
        }
        //special property to return entity count info
        if (propertyName.equals(XalanConstants.JDK_ENTITY_COUNT_INFO)) {
            return indexEntityCountInfo;
        }
        return -1;
    }

    /**
     * Check if there's no limit defined by the Security Manager
     * @param limit
     * @return
     */
    public boolean isNoLimit(int limit) {
        return limit == NO_LIMIT;
    }
    /**
     * Check if the size (length or count) of the specified limit property is
     * over the limit
     *
     * @param limit the type of the limit property
     * @param entityName the name of the entity
     * @param size the size (count or length) of the entity
     * @return true if the size is over the limit, false otherwise
     */
    public boolean isOverLimit(Limit limit, String entityName, int size,
            XMLLimitAnalyzer limitAnalyzer) {
        return isOverLimit(limit.ordinal(), entityName, size, limitAnalyzer);
    }

    /**
     * Check if the value (length or count) of the specified limit property is
     * over the limit
     *
     * @param index the index of the limit property
     * @param entityName the name of the entity
     * @param size the size (count or length) of the entity
     * @return true if the size is over the limit, false otherwise
     */
    public boolean isOverLimit(int index, String entityName, int size,
            XMLLimitAnalyzer limitAnalyzer) {
        if (values[index] == NO_LIMIT) {
            return false;
        }
        if (size > values[index]) {
            limitAnalyzer.addValue(index, entityName, size);
            return true;
        }
        return false;
    }

    /**
     * Check against cumulated value
     *
     * @param limit the type of the limit property
     * @param size the size (count or length) of the entity
     * @return true if the size is over the limit, false otherwise
     */
    public boolean isOverLimit(Limit limit, XMLLimitAnalyzer limitAnalyzer) {
        return isOverLimit(limit.ordinal(), limitAnalyzer);
    }

    public boolean isOverLimit(int index, XMLLimitAnalyzer limitAnalyzer) {
        if (values[index] == NO_LIMIT) {
            return false;
        }

        if (index == Limit.ELEMENT_ATTRIBUTE_LIMIT.ordinal() ||
                index == Limit.ENTITY_EXPANSION_LIMIT.ordinal() ||
                index == Limit.TOTAL_ENTITY_SIZE_LIMIT.ordinal() ||
                index == Limit.ENTITY_REPLACEMENT_LIMIT.ordinal() ||
                index == Limit.MAX_ELEMENT_DEPTH_LIMIT.ordinal() ||
                index == Limit.MAX_NAME_LIMIT.ordinal()
                ) {
            return (limitAnalyzer.getTotalValue(index) > values[index]);
        } else {
            return (limitAnalyzer.getValue(index) > values[index]);
        }
    }

    public void debugPrint(XMLLimitAnalyzer limitAnalyzer) {
        if (printEntityCountInfo.equals(XalanConstants.JDK_YES)) {
            limitAnalyzer.debugPrint(this);
        }
    }


    /**
     * Indicate if a property is set explicitly
     * @param index
     * @return
     */
    public boolean isSet(int index) {
        return isSet[index];
    }

    public boolean printEntityCountInfo() {
        return printEntityCountInfo.equals(XalanConstants.JDK_YES);
    }

    /**
     * Read from system properties, or those in jaxp.properties
     */
    private void readSystemProperties() {

        for (Limit limit : Limit.values()) {
            if (!getSystemProperty(limit, limit.systemProperty())) {
                //if system property is not found, try the older form if any
                for (NameMap nameMap : NameMap.values()) {
                    String oldName = nameMap.getOldName(limit.systemProperty());
                    if (oldName != null) {
                        getSystemProperty(limit, oldName);
                    }
                }
            }
        }

    }

    // Array list to store printed warnings for each SAX parser used
    private static final CopyOnWriteArrayList<String> printedWarnings = new CopyOnWriteArrayList<>();

    /**
     * Prints out warnings if a parser does not support the specified feature/property.
     *
     * @param parserClassName the name of the parser class
     * @param propertyName the property name
     * @param exception the exception thrown by the parser
     */
    public static void printWarning(String parserClassName, String propertyName, SAXException exception) {
        String key = parserClassName+":"+propertyName;
        if (printedWarnings.addIfAbsent(key)) {
            System.err.println( "Warning: "+parserClassName+": "+exception.getMessage());
        }
    }

    /**
     * Read from system properties, or those in jaxp.properties
     *
     * @param property the type of the property
     * @param sysPropertyName the name of system property
     */
    private boolean getSystemProperty(Limit limit, String sysPropertyName) {
        try {
            String value = SecuritySupport.getSystemProperty(sysPropertyName);
            if (value != null && !value.equals("")) {
                values[limit.ordinal()] = Integer.parseInt(value);
                states[limit.ordinal()] = State.SYSTEMPROPERTY;
                return true;
            }

            value = SecuritySupport.readJAXPProperty(sysPropertyName);
            if (value != null && !value.equals("")) {
                values[limit.ordinal()] = Integer.parseInt(value);
                states[limit.ordinal()] = State.JAXPDOTPROPERTIES;
                return true;
            }
        } catch (NumberFormatException e) {
            //invalid setting
            throw new NumberFormatException("Invalid setting for system property: " + limit.systemProperty());
        }
        return false;
    }


    /**
     * Convert a value set through setProperty to XMLSecurityManager.
     * If the value is an instance of XMLSecurityManager, use it to override the default;
     * If the value is an old SecurityManager, convert to the new XMLSecurityManager.
     *
     * @param value user specified security manager
     * @param securityManager an instance of XMLSecurityManager
     * @return an instance of the new security manager XMLSecurityManager
     */
    public static XMLSecurityManager convert(Object value, XMLSecurityManager securityManager) {
        if (value == null) {
            if (securityManager == null) {
                securityManager = new XMLSecurityManager(true);
            }
            return securityManager;
        }
        if (value instanceof XMLSecurityManager) {
            return (XMLSecurityManager)value;
        } else {
            if (securityManager == null) {
                securityManager = new XMLSecurityManager(true);
            }
            if (value instanceof SecurityManager) {
                SecurityManager origSM = (SecurityManager)value;
                securityManager.setLimit(Limit.MAX_OCCUR_NODE_LIMIT, State.APIPROPERTY, origSM.getMaxOccurNodeLimit());
                securityManager.setLimit(Limit.ENTITY_EXPANSION_LIMIT, State.APIPROPERTY, origSM.getEntityExpansionLimit());
                securityManager.setLimit(Limit.ELEMENT_ATTRIBUTE_LIMIT, State.APIPROPERTY, origSM.getElementAttrLimit());
            }
            return securityManager;
        }
    }
}
