package tools.jackson.databind.util;

import java.util.*;

import tools.jackson.core.SerializableString;

import tools.jackson.databind.*;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.AnnotatedClass;

/**
 * Helper class used for storing String serializations of {@code Enum}s,
 * to match to/from external representations.
 */
public final class EnumValues
    implements java.io.Serializable
{
    private static final long serialVersionUID = 1;

    private final Class<Enum<?>> _enumClass;

    private final Enum<?>[] _values;
    private final SerializableString[] _textual;

    private transient EnumMap<?,SerializableString> _asMap;

    private EnumValues(Class<Enum<?>> enumClass, SerializableString[] textual)
    {
        _enumClass = enumClass;
        _values = enumClass.getEnumConstants();
        _textual = textual;
    }

    /**
     * NOTE: do NOT call this if configuration may change, and choice between toString()
     *   and name() might change dynamically.
     */
    public static EnumValues construct(SerializationConfig config, AnnotatedClass enumClass) {
        if (config.isEnabled(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)) {
            return constructFromToString(config, enumClass);
        }
        return constructFromName(config, enumClass);
    }

    /**
     * @since 2.16
     */
    public static EnumValues constructFromName(MapperConfig<?> config, AnnotatedClass annotatedClass) 
    {
        // prepare data
        final AnnotationIntrospector ai = config.getAnnotationIntrospector();
        final boolean useLowerCase = config.isEnabled(EnumFeature.WRITE_ENUMS_TO_LOWERCASE);
        final Class<?> enumCls0 = annotatedClass.getRawType();
        final Class<Enum<?>> enumCls = _enumClass(enumCls0);
        final Enum<?>[] enumConstants = _enumConstants(enumCls0);

        // introspect
        String[] names = ai.findEnumValues(config, annotatedClass, 
                enumConstants, new String[enumConstants.length]);

        // build
        SerializableString[] textual = new SerializableString[enumConstants.length];
        for (int i = 0, len = enumConstants.length; i < len; ++i) {
            Enum<?> enumValue = enumConstants[i];
            String name = names[i];
            if (name == null) {
                name = enumValue.name();
            }
            if (useLowerCase) {
                name = name.toLowerCase();
            }
            textual[enumValue.ordinal()] = config.compileString(name);
        }
        return construct(enumCls, textual);
    }

    /**
     * @since 2.16
     */
    public static EnumValues constructFromToString(MapperConfig<?> config, AnnotatedClass annotatedClass)
    {
        // prepare data
        final AnnotationIntrospector ai = config.getAnnotationIntrospector();
        final boolean useLowerCase = config.isEnabled(EnumFeature.WRITE_ENUMS_TO_LOWERCASE);
        final Class<?> enumCls0 = annotatedClass.getRawType();
        final Class<Enum<?>> enumCls = _enumClass(enumCls0);
        final Enum<?>[] enumConstants = _enumConstants(enumCls0);

        // introspect
        String[] names = new String[enumConstants.length];
        if (ai != null) {
            ai.findEnumValues(config, annotatedClass, enumConstants, names);
        }

        // build
        SerializableString[] textual = new SerializableString[enumConstants.length];
        for (int i = 0; i < enumConstants.length; i++) {
            String name = names[i];
            if (name == null) {
                Enum<?> en = enumConstants[i];
                name = en.toString();
            }
            if (useLowerCase) {
                name = name.toLowerCase();
            }
            textual[i] = config.compileString(name);
        }
        return construct(enumCls, textual);
    }

    /**
     * Returns String serializations of Enum name using an instance of {@link EnumNamingStrategy}.
     *
     * The output {@link EnumValues} should contain values that are symmetric to
     * {@link EnumResolver#constructUsingEnumNamingStrategy(DeserializationConfig, Class, EnumNamingStrategy)}.
     *
     * @since 2.15
     */
    public static EnumValues constructUsingEnumNamingStrategy(MapperConfig<?> config, Class<Enum<?>> enumClass, EnumNamingStrategy namingStrategy) {
        Class<? extends Enum<?>> cls = ClassUtil.findEnumType(enumClass);
        Enum<?>[] values = cls.getEnumConstants();
        if (values == null) {
            throw new IllegalArgumentException("Cannot determine enum constants for Class " + enumClass.getName());
        }
        ArrayList<String> external = new ArrayList<>(values.length);
        for (Enum<?> en : values) {
            external.add(namingStrategy.convertEnumToExternalName(en.name()));
        }
        return construct(config, enumClass, external);
    }

    /**
     * @since 2.11
     */
    public static EnumValues construct(MapperConfig<?> config, Class<Enum<?>> enumClass,
            List<String> externalValues) {
        final int len = externalValues.size();
        SerializableString[] textual = new SerializableString[len];
        for (int i = 0; i < len; ++i) {
            textual[i] = config.compileString(externalValues.get(i));
        }
        return construct(enumClass, textual);
    }

    /**
     * @since 2.11
     */
    public static EnumValues construct(Class<Enum<?>> enumClass,
            SerializableString[] externalValues) {
        return new EnumValues(enumClass, externalValues);
    }

    /* 
    /**********************************************************************
    /* Internal Helpers
    /**********************************************************************
     */

    @SuppressWarnings("unchecked")
    protected static Class<Enum<?>> _enumClass(Class<?> enumCls0) {
        return (Class<Enum<?>>) enumCls0;
    }

    /**
     * Helper method <b>slightly</b> different from {@link EnumResolver#_enumConstants(Class)},
     * with same method name to keep calling methods more consistent.
     */
    protected static Enum<?>[] _enumConstants(Class<?> enumCls) {
        final Enum<?>[] enumValues = ClassUtil.findEnumType(enumCls).getEnumConstants();
        if (enumValues == null) {
            throw new IllegalArgumentException("No enum constants for class "+enumCls.getName());
        }
        return enumValues;
    }
    
    /*
    /**********************************************************************
    /* Public API
    /**********************************************************************
     */

    public SerializableString serializedValueFor(Enum<?> key) {
        return _textual[key.ordinal()];
    }

    public Collection<SerializableString> values() {
        return Arrays.asList(_textual);
    }

    /**
     * Convenience accessor for getting raw Enum instances.
     */
    public List<Enum<?>> enums() {
        return Arrays.asList(_values);
    }

    /**
     * Method used for serialization and introspection by core Jackson code.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public EnumMap<?,SerializableString> internalMap() {
        EnumMap<?,SerializableString> result = _asMap;
        if (result == null) {
            // Alas, need to create it in a round-about way, due to typing constraints...
            Map<Enum<?>,SerializableString> map = new LinkedHashMap<Enum<?>,SerializableString>();
            for (Enum<?> en : _values) {
                map.put(en, _textual[en.ordinal()]);
            }
            _asMap = result = new EnumMap(map);
        }
        return result;
    }

    public Class<Enum<?>> getEnumClass() { return _enumClass; }
}
