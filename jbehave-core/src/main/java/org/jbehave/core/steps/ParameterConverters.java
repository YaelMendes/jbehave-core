package org.jbehave.core.steps;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.google.gson.Gson;
import org.apache.commons.lang3.BooleanUtils;
import org.jbehave.core.annotations.AsJson;
import org.jbehave.core.annotations.AsParameters;
import org.jbehave.core.configuration.Configuration;
import org.jbehave.core.configuration.Keywords;
import org.jbehave.core.configuration.MostUsefulConfiguration;
import org.jbehave.core.i18n.LocalizedKeywords;
import org.jbehave.core.io.LoadFromClasspath;
import org.jbehave.core.io.ResourceLoader;
import org.jbehave.core.model.ExamplesTable;
import org.jbehave.core.model.ExamplesTableFactory;
import org.jbehave.core.model.TableTransformers;

import static java.util.Arrays.asList;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * <p>
 * Facade responsible for converting parameter values to Java objects. It allows
 * the registration of several {@link ParameterConverter} instances, and the
 * first one that is found to matches the appropriate parameter type is used.
 * </p>
 * <p>
 * Converters for several Java types are provided out-of-the-box:
 * <ul>
 * <li>{@link ParameterConverters.NumberConverter NumberConverter}</li>
 * <li>{@link ParameterConverters.StringConverter StringConverter}</li>
 * <li>{@link ParameterConverters.StringListConverter StringListConverter}</li>
 * <li>{@link ParameterConverters.DateConverter DateConverter}</li>
 * <li>{@link ParameterConverters.ExamplesTableConverter ExamplesTableConverter}
 * </li>
 * <li>{@link ParameterConverters.ExamplesTableParametersConverter
 * ExamplesTableParametersConverter}</li>
 * <li>{@link ParameterConverters.MethodReturningConverter
 * MethodReturningConverter}</li>
 * </ul>
 * </p>
 */
public class ParameterConverters {

    public static final StepMonitor DEFAULT_STEP_MONITOR = new SilentStepMonitor();
    public static final Locale DEFAULT_NUMBER_FORMAT_LOCAL = Locale.ENGLISH;
    public static final String DEFAULT_COLLECTION_SEPARATOR = ",";

    /**
     * Use {@link #DEFAULT_COLLECTION_SEPARATOR}
     */
    @Deprecated
    public static final String DEFAULT_LIST_SEPARATOR = DEFAULT_COLLECTION_SEPARATOR;

    public static final boolean DEFAULT_THREAD_SAFETY = true;

    private static final String DEFAULT_TRUE_VALUE = "true";
    private static final String DEFAULT_FALSE_VALUE = "false";

    private final StepMonitor monitor;
    private final List<ParameterConverter> converters;
    private final boolean threadSafe;
    private String escapedCollectionSeparator;


    /**
     * Creates a ParameterConverters using the default resource loader and table transformers,
     * a SilentStepMonitor, English as Locale and "," as collection separator.
     */
    public ParameterConverters() {
        this(new LoadFromClasspath(), new TableTransformers());
    }

    /**
     * Creates a ParameterConverters using the given table transformers.
     *
     * @param tableTransformers the table transformers
     */
    public ParameterConverters(TableTransformers tableTransformers) {
        this(new LoadFromClasspath(), tableTransformers);
    }

    /**
     * Creates a ParameterConverters of ParameterConverters using the given resource loader.
     *
     * @param resourceLoader the resource loader
     */
    public ParameterConverters(ResourceLoader resourceLoader) {
        this(resourceLoader, new TableTransformers());
    }

    /**
     * Creates a ParameterConverters given resource loader and table transformers.
     *
     * @param resourceLoader the resource loader
     * @param tableTransformers the table transformers
     */
    public ParameterConverters(ResourceLoader resourceLoader, TableTransformers tableTransformers) {
        this(DEFAULT_STEP_MONITOR, resourceLoader, new ParameterControls(), tableTransformers);
    }

    /**
     * Creates a ParameterConverters using given StepMonitor, resource loader and table transformers.
     *
     * @param monitor the StepMonitor to use
     * @param resourceLoader the resource loader
     * @param parameterControls the parameter controls
     * @param tableTransformers the table transformers
     */
    public ParameterConverters(StepMonitor monitor, ResourceLoader resourceLoader, ParameterControls parameterControls,
            TableTransformers tableTransformers) {
        this(monitor, resourceLoader, parameterControls, tableTransformers, DEFAULT_NUMBER_FORMAT_LOCAL,
                DEFAULT_COLLECTION_SEPARATOR, DEFAULT_THREAD_SAFETY);
    }

    /**
     * Create a ParameterConverters with given thread-safety
     *
     * @param resourceLoader the resource loader
     * @param parameterControls the parameter controls
     * @param tableTransformers the table transformers
     * @param threadSafe the boolean flag to determine if access to
     * {@link ParameterConverter} should be thread-safe
     */
    public ParameterConverters(ResourceLoader resourceLoader, ParameterControls parameterControls,
            TableTransformers tableTransformers, boolean threadSafe) {
        this(DEFAULT_STEP_MONITOR, resourceLoader, parameterControls, tableTransformers, DEFAULT_NUMBER_FORMAT_LOCAL,
                DEFAULT_COLLECTION_SEPARATOR, threadSafe);
    }

    /**
     * Creates a ParameterConverters for the given StepMonitor, Locale, list
     * separator and thread-safety. When selecting a collectionSeparator, please make
     * sure that this character doesn't have a special meaning in your Locale
     * (for instance "," is used as decimal separator in some Locale)
     *
     * @param monitor the StepMonitor reporting the conversions
     * @param resourceLoader the resource loader
     * @param parameterControls the parameter controls
     * @param tableTransformers the table transformers
     * @param locale the Locale to use when reading numbers
     * @param collectionSeparator the String to use as collection separator
     * @param threadSafe the boolean flag to determine if modification of
     * {@link ParameterConverter} should be thread-safe
     */
    public ParameterConverters(StepMonitor monitor, ResourceLoader resourceLoader, ParameterControls parameterControls,
            TableTransformers tableTransformers, Locale locale, String collectionSeparator, boolean threadSafe) {
        this(monitor, new ArrayList<ParameterConverter>(), threadSafe);
        this.addConverters(
                defaultConverters(resourceLoader, parameterControls, tableTransformers, locale, collectionSeparator));
    }

    private ParameterConverters(StepMonitor monitor, List<ParameterConverter> converters, boolean threadSafe) {
        this.monitor = monitor;
        this.threadSafe = threadSafe;
        this.converters = threadSafe ? new CopyOnWriteArrayList<>(converters)
                : new ArrayList<>(converters);
    }

    protected ParameterConverter[] defaultConverters(ResourceLoader resourceLoader, ParameterControls parameterControls,
            TableTransformers tableTransformers, Locale locale, String collectionSeparator) {
        this.escapedCollectionSeparator = escapeRegexPunctuation(collectionSeparator);
        ExamplesTableFactory tableFactory = new ExamplesTableFactory(resourceLoader, this, parameterControls,
                tableTransformers);
        JsonFactory jsonFactory = new JsonFactory();
        return new ParameterConverter[] { new BooleanConverter(),
                new NumberConverter(NumberFormat.getInstance(locale)),
                new StringConverter(),
                new StringListConverter(escapedCollectionSeparator),
                new DateConverter(),
                new EnumConverter(),
                new ExamplesTableConverter(tableFactory),
                new ExamplesTableParametersConverter(tableFactory),
                new JsonConverter(jsonFactory) };
    }

    // TODO : This is a duplicate from RegExpPrefixCapturing
    private String escapeRegexPunctuation(String matchThis) {
        return matchThis.replaceAll("([\\[\\]\\{\\}\\?\\^\\.\\*\\(\\)\\+\\\\])", "\\\\$1");
    }

    public ParameterConverters addConverters(ParameterConverter... converters) {
        return addConverters(asList(converters));
    }

    public ParameterConverters addConverters(List<ParameterConverter> converters) {
        this.converters.addAll(0, converters);
        return this;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Object convert(String value, Type type) {

        ParameterConverter<?> converter = findConverter(type);
        if (converter != null) {
            Object converted = converter.convertValue(value, type);
            monitor.convertedValueOfType(value, type, converted, converter.getClass());
            return converted;
        }

        if (isAssignableFromRawType(Collection.class, type)) {
            Type elementType = argumentType(type);
            ParameterConverter elementConverter = findConverter(elementType);
            Collection collection = createCollection(rawClass(type));
            if (elementConverter != null && collection != null) {
                fillCollection(value, escapedCollectionSeparator, elementConverter, elementType, collection);
                return collection;
            }
        }

        throw new ParameterConvertionFailed("No parameter converter for " + type);
    }

    private ParameterConverter<?> findConverter(Type type) {
        for (ParameterConverter<?> converter : converters) {
            if (converter.accept(type)) {
                return converter;
            }
        }
        return null;
    }

    private static boolean isAssignableFrom(Class<?> clazz, Type type) {
        return type instanceof Class<?> && clazz.isAssignableFrom((Class<?>) type);
    }

    private static boolean isAssignableFromRawType(Class<?> clazz, Type type) {
        return type instanceof ParameterizedType && isAssignableFrom(clazz, ((ParameterizedType) type).getRawType());
    }

    private static Class<?> rawClass(Type type) {
        return (Class<?>) ((ParameterizedType) type).getRawType();
    }

    private static Type argumentType(Type type) {
        return ((ParameterizedType) type).getActualTypeArguments()[0];
    }

    private static <T> void fillCollection(String value, String valueSeparator, ParameterConverter<T> elementConverter,
            Type elementType, Collection<T> convertedValues) {
        if (!value.trim().isEmpty()) {
            for (String elementValue : value.split(valueSeparator)) {
                T convertedValue = elementConverter.convertValue(elementValue.trim(), elementType);
                convertedValues.add(convertedValue);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <E> Collection<E> createCollection(Class<?> collectionType) {
        if (collectionType.isInterface()) {
            if (Set.class == collectionType) {
                return new HashSet<>();
            }
            else if (List.class == collectionType) {
                return new ArrayList<>();
            }
            else if (SortedSet.class == collectionType || NavigableSet.class == collectionType) {
                return new TreeSet<>();
            }
        }
        try {
            return (Collection<E>) collectionType.getConstructor().newInstance();
        }
        catch (@SuppressWarnings("unused") Throwable t) {
            // Could not instantiate Collection type, swallowing exception quietly
        }
        return null;
    }

    public ParameterConverters newInstanceAdding(ParameterConverter converter) {
        List<ParameterConverter> convertersForNewInstance = new ArrayList<>(converters);
        convertersForNewInstance.add(converter);
        return new ParameterConverters(monitor, convertersForNewInstance, threadSafe);
    }

    public interface ParameterConverter<T> {

        boolean accept(Type type);

        T convertValue(String value, Type type);
    }

    @SuppressWarnings("serial")
    public static class ParameterConvertionFailed extends RuntimeException {

        public ParameterConvertionFailed(String message) {
            super(message);
        }

        public ParameterConvertionFailed(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static abstract class AbstractParameterConverter<T> implements ParameterConverter<T> {

        private final Type acceptedType;

        public AbstractParameterConverter() {
            this.acceptedType = getParameterizedType(getClass()).getActualTypeArguments()[0];
        }

        private ParameterizedType getParameterizedType(Class<?> clazz) {
            Type genericSuperclass = clazz.getGenericSuperclass();
            return genericSuperclass instanceof ParameterizedType ? (ParameterizedType) genericSuperclass
                    : getParameterizedType(clazz.getSuperclass());
        }

        @Override
        public boolean accept(Type type) {
            if (acceptedType instanceof Class<?>) {
                return isAssignableFrom((Class<?>) acceptedType, type);
            }
            return acceptedType.equals(type);
        }
    }

    public static abstract class AbstractListParameterConverter<T> implements ParameterConverter<List<T>> {

        private final String valueSeparator;
        private final ParameterConverter<T> elementConverter;

        public AbstractListParameterConverter(String valueSeparator, ParameterConverter<T> elementConverter) {
            this.valueSeparator = valueSeparator;
            this.elementConverter = elementConverter;
        }

        @Override
        public boolean accept(Type type) {
            return isAssignableFromRawType(List.class, type) && elementConverter.accept(argumentType(type));
        }

        @Override
        public List<T> convertValue(String value, Type type) {
            Type elementType = argumentType(type);
            List<T> convertedValues = new ArrayList<>();
            fillCollection(value, valueSeparator, elementConverter, elementType, convertedValues);
            return convertedValues;
        }
    }

    /**
     * <p>
     * Converts values to numbers, supporting any subclass of {@link Number}
     * (including generic Number type), and it unboxed counterpart, using a
     * {@link NumberFormat} to parse to a {@link Number} and to convert it to a
     * specific number type:
     * <ul>
     * <li>Byte, byte: {@link Number#byteValue()}</li>
     * <li>Short, short: {@link Number#shortValue()}</li>
     * <li>Integer, int: {@link Number#intValue()}</li>
     * <li>Float, float: {@link Number#floatValue()}</li>
     * <li>Long, long: {@link Number#longValue()}</li>
     * <li>Double, double: {@link Number#doubleValue()}</li>
     * <li>BigInteger: {@link BigInteger#valueOf(long)}</li>
     * <li>BigDecimal: {@link BigDecimal#valueOf(double)}</li>
     * </ul>
     * If no number format is provided, it defaults to
     * {@link NumberFormat#getInstance(Locale.ENGLISH)}.
     * <p>
     * The localized instance {@link NumberFormat#getInstance(Locale)} can be
     * used to convert numbers in specific locales.
     * </p>
     */
    public static class NumberConverter extends AbstractParameterConverter<Number> {
        private static List<Class<?>> primitiveTypes = asList(new Class<?>[] { byte.class, short.class, int.class,
                float.class, long.class, double.class });

        private final NumberFormat numberFormat;
        private ThreadLocal<NumberFormat> threadLocalNumberFormat = new ThreadLocal<>();

        public NumberConverter() {
            this(NumberFormat.getInstance(DEFAULT_NUMBER_FORMAT_LOCAL));
        }

        public NumberConverter(NumberFormat numberFormat) {
            synchronized (this) {
                this.numberFormat = numberFormat;
                this.threadLocalNumberFormat.set((NumberFormat) this.numberFormat.clone());
            }
        }

        @Override
        public boolean accept(Type type) {
            return super.accept(type) || primitiveTypes.contains(type);
        }

        @Override
        public Number convertValue(String value, Type type) {
            try {
                Number n = numberFormat().parse(value);
                if (type == Byte.class || type == byte.class) {
                    return n.byteValue();
                } else if (type == Short.class || type == short.class) {
                    return n.shortValue();
                } else if (type == Integer.class || type == int.class) {
                    return n.intValue();
                } else if (type == Float.class || type == float.class) {
                    return n.floatValue();
                } else if (type == Long.class || type == long.class) {
                    return n.longValue();
                } else if (type == Double.class || type == double.class) {
                    return n.doubleValue();
                } else if (type == BigInteger.class) {
                    return BigInteger.valueOf(n.longValue());
                } else if (type == BigDecimal.class) {
                    return new BigDecimal(canonicalize(value));
                } else if (type == AtomicInteger.class) {
                    return new AtomicInteger(Integer.parseInt(value));
                } else if (type == AtomicLong.class) {
                    return new AtomicLong(Long.parseLong(value));
                } else {
                    return n;
                }
            } catch (NumberFormatException | ParseException e) {
                throw new ParameterConvertionFailed(value, e);
            }
        }

        /**
         * Return NumberFormat instance with preferred locale threadsafe
         *
         * @return A threadlocal version of original NumberFormat instance
         */
        private NumberFormat numberFormat() {
            if (threadLocalNumberFormat.get() == null) {
                synchronized (this) {
                    threadLocalNumberFormat.set((NumberFormat) numberFormat.clone());
                }
            }
            return threadLocalNumberFormat.get();
        }

        /**
         * Canonicalize a number representation to a format suitable for the
         * {@link BigDecimal(String)} constructor, taking into account the
         * settings of the currently configured DecimalFormat.
         *
         * @param value a localized number value
         * @return A canonicalized string value suitable for consumption by
         * BigDecimal
         */
        private String canonicalize(String value) {
            char decimalPointSeparator = '.'; // default
            char minusSign = '-'; // default
            String rxNotDigits = "[\\.,]";
            StringBuilder builder = new StringBuilder(value.length());

            // override defaults according to numberFormat's settings
            if (numberFormat() instanceof DecimalFormat) {
                DecimalFormatSymbols decimalFormatSymbols = ((DecimalFormat) numberFormat()).getDecimalFormatSymbols();
                minusSign = decimalFormatSymbols.getMinusSign();
                decimalPointSeparator = decimalFormatSymbols.getDecimalSeparator();
            }

            value = value.trim();
            int decimalPointPosition = value.lastIndexOf(decimalPointSeparator);
            int firstDecimalPointPosition = value.indexOf(decimalPointSeparator);

            if (firstDecimalPointPosition != decimalPointPosition) {
                throw new NumberFormatException("Invalid format, more than one decimal point has been found.");
            }

            if (decimalPointPosition != -1) {
                String sf = value.substring(0, decimalPointPosition).replaceAll(rxNotDigits, "");
                String dp = value.substring(decimalPointPosition + 1).replaceAll(rxNotDigits, "");

                builder.append(sf);
                builder.append('.'); // fixed "." for BigDecimal constructor
                builder.append(dp);

            } else {
                builder.append(value.replaceAll(rxNotDigits, ""));
            }

            boolean isNegative = value.charAt(0) == minusSign;

            if (isNegative) {
                builder.setCharAt(0, '-'); // fixed "-" for BigDecimal constructor
            }
            return builder.toString();
        }
    }

    /**
     * Converts value to list of numbers. Splits value to a list, using an
     * injectable value separator (defaulting to ",") and converts each element
     * of list via the {@link NumberConverter}, using the {@link NumberFormat}
     * provided (defaulting to {@link NumberFormat#getInstance(Locale.ENGLISH)}
     * ).
     */
    public static class NumberListConverter extends AbstractListParameterConverter<Number> {

        public NumberListConverter() {
            this(NumberFormat.getInstance(DEFAULT_NUMBER_FORMAT_LOCAL), DEFAULT_COLLECTION_SEPARATOR);
        }

        /**
         * @param numberFormat Specific NumberFormat to use.
         * @param valueSeparator A regexp to use as list separate
         */
        public NumberListConverter(NumberFormat numberFormat, String valueSeparator) {
            super(valueSeparator, new NumberConverter(numberFormat));
        }
    }

    public static class StringConverter extends AbstractParameterConverter<String> {
        private static final String NEWLINES_PATTERN = "(\n)|(\r\n)";
        private static final String SYSTEM_NEWLINE = System.getProperty("line.separator");

        public String convertValue(String value, Type type) {
            return value.replaceAll(NEWLINES_PATTERN, SYSTEM_NEWLINE);
        }
    }

    /**
     * Converts value to list of String. Splits value to a list, using an
     * injectable value separator (defaults to ",") and trimming each element of
     * the list.
     */
    public static class StringListConverter extends AbstractListParameterConverter<String> {

        public StringListConverter() {
            this(DEFAULT_COLLECTION_SEPARATOR);
        }

        /**
         * @param valueSeparator A regexp to use as list separator
         */
        public StringListConverter(String valueSeparator) {
            super(valueSeparator, new StringConverter());
        }

        @Override
        public List<String> convertValue(String value, Type type)
        {
            if (value.trim().isEmpty()) {
                return Collections.emptyList();
            }
            return super.convertValue(value, type);
        }
    }

    /**
     * Use #AbstractListParameterConverter
     * @param values values to trim
     * @return trimmed values
     */
    @Deprecated
    public static List<String> trim(List<String> values) {
        List<String> trimmed = new ArrayList<>();
        for (String value : values) {
            trimmed.add(value.trim());
        }
        return trimmed;
    }

    /**
     * Parses value to a {@link Date} using an injectable {@link DateFormat}
     * (defaults to <b>new SimpleDateFormat("dd/MM/yyyy")</b>)
     */
    public static class DateConverter extends AbstractParameterConverter<Date> {

        public static final DateFormat DEFAULT_FORMAT = new SimpleDateFormat("dd/MM/yyyy");

        private final DateFormat dateFormat;

        public DateConverter() {
            this(DEFAULT_FORMAT);
        }

        public DateConverter(DateFormat dateFormat) {
            this.dateFormat = dateFormat;
        }

        @Override
        public Date convertValue(String value, Type type) {
            try {
                return dateFormat.parse(value);
            } catch (ParseException e) {
                throw new ParameterConvertionFailed("Failed to convert value " + value + " with date format "
                        + (dateFormat instanceof SimpleDateFormat ? ((SimpleDateFormat) dateFormat).toPattern()
                                : dateFormat), e);
            }
        }

    }

    public static class BooleanConverter extends AbstractParameterConverter<Boolean> {
        private final String trueValue;
        private final String falseValue;

        public BooleanConverter() {
            this(DEFAULT_TRUE_VALUE, DEFAULT_FALSE_VALUE);
        }

        public BooleanConverter(String trueValue, String falseValue) {
            this.trueValue = trueValue;
            this.falseValue = falseValue;
        }

        @Override
        public boolean accept(Type type) {
            return super.accept(type) || isAssignableFrom(Boolean.TYPE, type);
        }

        @Override
        public Boolean convertValue(String value, Type type) {
            try {
                return BooleanUtils.toBoolean(value, trueValue, falseValue);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
    }

    public static class BooleanListConverter extends AbstractListParameterConverter<Boolean> {

        public BooleanListConverter() {
            this(DEFAULT_COLLECTION_SEPARATOR, DEFAULT_TRUE_VALUE, DEFAULT_FALSE_VALUE);
        }

        public BooleanListConverter(String valueSeparator) {
            this(valueSeparator, DEFAULT_TRUE_VALUE, DEFAULT_FALSE_VALUE);
        }

        public BooleanListConverter(String valueSeparator, String trueValue, String falseValue) {
            super(valueSeparator, new BooleanConverter(trueValue, falseValue));
        }
    }

    /**
     * Parses value to any {@link Enum}
     */
    public static class EnumConverter implements ParameterConverter<Enum<?>> {

        @Override
        public boolean accept(Type type) {
            return type instanceof Class<?> && ((Class<?>) type).isEnum();
        }

        @Override
        public Enum<?> convertValue(String value, Type type) {
            String typeClass = ((Class<?>) type).getName();
            Class<?> enumClass = (Class<?>) type;
            Method valueOfMethod = null;
            try {
                valueOfMethod = enumClass.getMethod("valueOf", String.class);
                valueOfMethod.setAccessible(true);
                return (Enum<?>) valueOfMethod.invoke(enumClass, new Object[] { value });
            } catch (Exception e) {
                throw new ParameterConvertionFailed("Failed to convert " + value + " for Enum " + typeClass, e);
            }
        }
    }

    /**
     * An {@link EnumConverter} allowing stories prose to be more natural.
     * Before performing the actual conversion, it transforms values to upper-case,
     * with any non-alphanumeric character replaced by an underscore ('_').
     * <p>
     * <b>Example</b>:
     * assuming we have defined the step "{@code Given I am on the $page}"
     * which is mapped to the method {@code iAmOnPage(PageEnum page)},
     * we can then write in a scenario:
     * <pre>{@code
     * Given I am on the login page
     * }</pre>
     * instead of:
     * <pre>{@code
     * Given I am on the LOGIN_PAGE
     * }</pre>
     * <p>
     * <b>Warning</b>. This <i>requires</i> enum constants to follow the
     * <a href="https://google-styleguide.googlecode.com/svn/trunk/javaguide.html#s5.2.4-constant-names">
     * standard conventions for constant names</a>, i.e. all uppercase letters,
     * with words separated by underscores.
     */
    public static class FluentEnumConverter extends EnumConverter {

        @Override
        public Enum<?> convertValue(String value, Type type) {
            return super.convertValue(value.replaceAll("\\W", "_").toUpperCase(), type);
        }
    }

    /**
     * Parses value to list of the same {@link Enum}, using an injectable value
     * separator (defaults to ",") and trimming each element of the list.
     */
    public static class EnumListConverter extends AbstractListParameterConverter<Enum<?>> {

        public EnumListConverter() {
            this(DEFAULT_COLLECTION_SEPARATOR);
        }

        public EnumListConverter(String valueSeparator) {
            super(valueSeparator, new EnumConverter());
        }
    }

    /**
     * Converts value to {@link ExamplesTable} using a
     * {@link ExamplesTableFactory}.
     */
    public static class ExamplesTableConverter extends AbstractParameterConverter<ExamplesTable> {

        private final ExamplesTableFactory factory;

        public ExamplesTableConverter(ExamplesTableFactory factory) {
            this.factory = factory;
        }

        @Override
        public ExamplesTable convertValue(String value, Type type) {
            return factory.createExamplesTable(value);
        }

    }

    /**
     * Converts ExamplesTable to list of parameters, mapped to annotated custom
     * types.
     */
    public static class ExamplesTableParametersConverter implements ParameterConverter<Object> {

        private final ExamplesTableFactory factory;

        public ExamplesTableParametersConverter(ExamplesTableFactory factory) {
            this.factory = factory;
        }

        @Override
        public boolean accept(Type type) {
            if (type instanceof ParameterizedType) {
                return rawClass(type).isAnnotationPresent(AsParameters.class) || argumentClass(type)
                        .isAnnotationPresent(AsParameters.class);
            }
            return type instanceof Class && ((Class<?>) type).isAnnotationPresent(AsParameters.class);
        }

        private Class<?> argumentClass(Type type) {
            if (type instanceof ParameterizedType) {
                return (Class<?>) ((ParameterizedType) type).getActualTypeArguments()[0];
            } else {
                return (Class<?>) type;
            }
        }

        @Override
        public Object convertValue(String value, Type type) {
            List<?> rows = factory.createExamplesTable(value).getRowsAs(argumentClass(type));
            if (type instanceof ParameterizedType) {
                return rows;
            }
            return rows.iterator().next();
        }

    }

    public static class JsonConverter implements ParameterConverter<Object> {

        private final JsonFactory factory;

        public JsonConverter() {
            this(new JsonFactory());
        }

        public JsonConverter(final JsonFactory factory) {
            this.factory = factory;
        }

        @Override
        public boolean accept(final Type type) {
            if (type instanceof ParameterizedType) {
                return rawClass(type).isAnnotationPresent(AsJson.class) || argumentClass(type).isAnnotationPresent(
                        AsJson.class);
            }
            return type instanceof Class && ((Class<?>) type).isAnnotationPresent(AsJson.class);
        }

        @Override
        public Object convertValue(final String value, final Type type) {
            return factory.createJson(value, type);
        }

        private Class<?> argumentClass(final Type type) {
            if (type instanceof ParameterizedType) {
                return (Class<?>) ((ParameterizedType) type).getActualTypeArguments()[0];
            } else {
                return (Class<?>) type;
            }
        }

    }

    public static class JsonFactory {

        private Keywords keywords;
        private final ResourceLoader resourceLoader;

        public JsonFactory() {
            this(new LocalizedKeywords());
        }

        public JsonFactory(final Keywords keywords) {
            this(keywords, new LoadFromClasspath());
        }

        public JsonFactory(final ResourceLoader resourceLoader) {
            this(new LocalizedKeywords(), resourceLoader);
        }

        public JsonFactory(final Keywords keywords, final ResourceLoader resourceLoader) {
            this.keywords = keywords;
            this.resourceLoader = resourceLoader;
        }

        public JsonFactory(final Configuration configuration) {
            this.keywords = configuration.keywords();
            this.resourceLoader = configuration.storyLoader();
        }

        public Object createJson(final String input, final Type type) {
            String jsonAsString;
            if (isBlank(input) || isJson(input)) {
                jsonAsString = input;
            } else {
                jsonAsString = resourceLoader.loadResourceAsText(input);
            }
            return new Gson().fromJson(jsonAsString, type);
        }

        protected boolean isJson(final String input) {
            return (input.startsWith("[") && input.endsWith("]")) || (input.startsWith("{") && input.endsWith("}"));
        }

        public void useKeywords(final Keywords keywords) {
            this.keywords = keywords;
        }

        public Keywords keywords() {
            return this.keywords;
        }
    }

    /**
     * Invokes method on instance to return value.
     */
    public static class MethodReturningConverter implements ParameterConverter<Object> {
        private Method method;
        private Class<?> stepsType;
        private InjectableStepsFactory stepsFactory;

        public MethodReturningConverter(Method method, Object instance) {
            this(method, instance.getClass(), new InstanceStepsFactory(new MostUsefulConfiguration(), instance));
        }

        public MethodReturningConverter(Method method, Class<?> stepsType, InjectableStepsFactory stepsFactory) {
            this.method = method;
            this.stepsType = stepsType;
            this.stepsFactory = stepsFactory;
        }

        @Override
        public boolean accept(Type type) {
            return isAssignableFrom(method.getReturnType(), type);
        }

        @Override
        public Object convertValue(String value, Type type) {
            try {
                Object instance = instance();
                return method.invoke(instance, value);
            } catch (Exception e) {
                throw new ParameterConvertionFailed("Failed to invoke method " + method + " with value " + value
                        + " in " + type, e);
            }
        }

        private Object instance() {
            return stepsFactory.createInstanceOfType(stepsType);
        }

    }

}
