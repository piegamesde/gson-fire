package io.gsonfire;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import io.gsonfire.annotations.Exclude;
import io.gsonfire.annotations.ExcludeDeserialize;
import io.gsonfire.annotations.ExcludeSerialize;
import io.gsonfire.gson.EnumDefaultValueTypeAdapterFactory;
import io.gsonfire.gson.ExcludeByValueTypeAdapterFactory;
import io.gsonfire.gson.FireExclusionStrategy;
import io.gsonfire.gson.FireExclusionStrategyComposite;
import io.gsonfire.gson.HooksTypeAdapterFactory;
import io.gsonfire.gson.SimpleIterableTypeAdapterFactory;
import io.gsonfire.gson.TypeSelectorTypeAdapterFactory;
import io.gsonfire.gson.WrapTypeAdapterFactory;
import io.gsonfire.postprocessors.MergeMapPostProcessor;
import io.gsonfire.postprocessors.methodinvoker.MethodInvokerPostProcessor;
import io.gsonfire.util.Mapper;
import io.gsonfire.util.reflection.CachedReflectionFactory;
import io.gsonfire.util.reflection.Factory;
import io.gsonfire.util.reflection.FieldInspector;

/**
 * @autor: julio
 */
public final class GsonFireBuilder {

    private final Map<Class, ClassConfig> classConfigMap = new HashMap<Class, ClassConfig>();
    private final Map<Class, Mapper> wrappedClasses = new HashMap<Class, Mapper>();
    private final List<Class> orderedClasses = new ArrayList<Class>();
    private final List<FireExclusionStrategy> serializationExclusions = new ArrayList<FireExclusionStrategy>();
    private final FieldInspector fieldInspector = new FieldInspector();
    private final Factory factory = new CachedReflectionFactory();
    private final Map<Class, Enum> enumDefaultValues = new HashMap<Class, Enum>();

    private DateSerializationPolicy dateSerializationPolicy;
    private boolean dateDeserializationStrict = true;
    private TimeZone serializeTimeZone = TimeZone.getDefault();
    private boolean enableExposeMethodResults = false;
	private boolean								enableExposeMethodParams			= false;
    private boolean enableExcludeByAnnotation = false;
    private boolean enableExclusionByValueStrategies = false;

    private ClassConfig getClassConfig(Class clazz){
        ClassConfig result = classConfigMap.get(clazz);
        if(result == null){
            result = new ClassConfig(clazz);
            classConfigMap.put(clazz, result);
            insertOrdered(orderedClasses, clazz);
        }
        return result;
    }

    private static void insertOrdered(List<Class> classes, Class clazz) {
        for(int i = classes.size() - 1; i >= 0; i--) {
            Class current = classes.get(i);
            if(current.isAssignableFrom(clazz)) {
                classes.add(i + 1, clazz);
                return;
            }
        }
        classes.add(0, clazz);
    }

    /**
     * Registers a Type selector for the Class specified. <br />
     * A type selector is in charge of deciding which sub class to use when converting a json
     * into an object.<br />
     * See <a href="http://goo.gl/qKo7z"> docs and example</a>
     * @param clazz
     * @param factory
     * @param <T>
     * @return
     */
    public <T> GsonFireBuilder registerTypeSelector(Class<T> clazz, TypeSelector<T> factory){
        ClassConfig config = getClassConfig(clazz);
        config.setTypeSelector(factory);
        return this;
    }

    /**
     * Registers a Post processor for the Class specified. <br />
     * A post processor is a class that will add new fields to a generated json just after generation, or that
     * will prepare a class just created from a json.<br />
     * See <a href="http://goo.gl/5fLLN"> docs and example</a>
     *
     * @param clazz
     * @param postProcessor
     * @param <T>
     * @return
     */
    public <T> GsonFireBuilder registerPostProcessor(Class<T> clazz, PostProcessor<? super T> postProcessor){
        ClassConfig config = getClassConfig(clazz);
        config.getPostProcessors().add(postProcessor);
        return this;
    }

    /**
     * Registers a pre processor for the Class specified. <br />
     * A pre processor is a class that will be given the gson to be deserialized in case it wants to change it before
     * it actually gets deserialized into a class
     * See <a href="http://goo.gl/b8V1AA"> docs and example</a>
     *
     * @param clazz
     * @param preProcessor
     * @param <T>
     * @return
     */
    public <T> GsonFireBuilder registerPreProcessor(Class<T> clazz, PreProcessor<? super T> preProcessor){
        ClassConfig config = getClassConfig(clazz);
        config.getPreProcessors().add(preProcessor);
        return this;
    }

    /**
     * Configures the resulting Gson to serialize/unserialize Date instances with a policy
     * @param policy
     * @return
     */
    public GsonFireBuilder dateSerializationPolicy(DateSerializationPolicy policy){
        dateSerializationPolicy = policy;
        return this;
    }

    /**
     * A given class will be wrapped/unwrapped with a given string
     * during serialization/deserialization.
     *
     * @param clazz
     * @param <T>
     * @return
     */
    public <T> GsonFireBuilder wrap(final Class<T> clazz, final String name) {
        wrap(clazz, new Mapper<T, String>() {
            @Override
            public String map(Object from) {
                return name;
            }
        });
        return this;
    }

    /**
     * A given class will be wrapped/unwrapped with a string generated by a mapper
     * during serialization/deserialization.
     *
     * @param clazz
     * @param mapper
     * @param <T>
     * @return
     */
    public <T> GsonFireBuilder wrap(Class<T> clazz, Mapper<T, String> mapper) {
        wrappedClasses.put(clazz, mapper);
        return this;
    }

    /**
     * By enabling this, all methods with the annotation {@link io.gsonfire.annotations.ExposeMethodResult} will
     * be evaluated and it result will be added to the resulting json
     * @return
     */
    public GsonFireBuilder enableExposeMethodResult(){
        this.enableExposeMethodResults = true;
        return this;
    }

    /**
	 * By enabling this, all methods with the annotation {@link io.gsonfire.annotations.ExposeMethodParam} will be called with appropriate data
	 * parsed from the json tree.
	 * 
	 * @return
	 */
	public GsonFireBuilder enableExposeMethodParam() {
		this.enableExposeMethodParams = true;
		return this;
	}

	/**
	 * By enabling this, all fields with the annotation {@link io.gsonfire.annotations.Exclude},
	 * {@link io.gsonfire.annotations.ExcludeSerialize} and {@link io.gsonfire.annotations.ExcludeDeserialize} will be evaluated and it result
	 * will be excluded from serialization, deserialization and/or both.
	 * <p/>
	 * This is equivalent to calling
	 * 
	 * <pre>
	 * builder.setExclusionStrategies(new AnnotationExclusionStrategy<Exclude>(Exclude.class));
	 * builder.addSerializationExclusionStrategy(new AnnotationExclusionStrategy<ExcludeSerialize>(ExcludeSerialize.class));
	 * builder.addDeserializationExclusionStrategy(new AnnotationExclusionStrategy<ExcludeDeserialize>(ExcludeDeserialize.class));
	 * </pre>
	 * 
	 * @return
	 */
    public GsonFireBuilder enableExcludeByAnnotation(){
        this.enableExcludeByAnnotation = true;
        return this;
    }

    /**
     * By enabling this, all exclusion by value strategies specified with the annotation
     * {@link io.gsonfire.annotations.ExcludeByValue} will be run to remove specific fields from the resulting json
     * @return
     */
    public GsonFireBuilder enableExclusionByValue(){
        this.enableExclusionByValueStrategies = true;
        return this;
    }

    /**
     * By enabling this, all methods with the annotation {@link io.gsonfire.annotations.ExposeMethodResult} will
     * be evaluated and it result will be added to the resulting json
     * @return
     */
    public GsonFireBuilder enableHooks(Class clazz){
        ClassConfig config = getClassConfig(clazz);
        config.setHooksEnabled(true);
        return this;
    }

    /**
     * By enabling this, when a class is being converted to Json and it contains a {@link java.util.Map} class
     * annotated with {@link io.gsonfire.annotations.MergeMap}, the map will be walked and merged
     * with the resulting json object.
     *
     * This method has been deprecated and a {@link io.gsonfire.PostProcessor} should be used instead
     * @return
     */
    @Deprecated
    public GsonFireBuilder enableMergeMaps(Class clazz){
        registerPostProcessor(clazz, new MergeMapPostProcessor(fieldInspector));
        return this;
    }

    /**
     * Sets the serialization TimeZone. This will affect only values that depend on the TimeZone, for example rfc3339
     * dates.
     * @param timeZone
     * @return
     */
    public GsonFireBuilder serializeTimeZone(TimeZone timeZone) {
        this.serializeTimeZone = timeZone;
        return this;
    }

    /**
     * Defines a default value for an enum when its String representation does not match any of the enum values.
     * The <code>defaultValue</code> can be null.
     * @param enumClass
     * @param defaultValue
     * @param <T>
     * @return
     */
    public <T extends Enum> GsonFireBuilder enumDefaultValue(Class<T> enumClass, T defaultValue) {
        this.enumDefaultValues.put(enumClass, defaultValue);
        return this;
    }

    public GsonFireBuilder addSerializationExclusionStrategy(FireExclusionStrategy exclusionStrategy) {
        this.serializationExclusions.add(exclusionStrategy);
        return this;
    }

    /**
     * Returns a new instance of the good old {@link GsonBuilder}
     * @return
     */
    public GsonBuilder createGsonBuilder(){
        Set<TypeToken> alreadyResolvedTypeTokensRegistry = Collections.newSetFromMap(new ConcurrentHashMap<TypeToken, Boolean>());
        GsonBuilder builder = new GsonBuilder();

		if (enableExposeMethodParams || enableExposeMethodResults) {
			FireExclusionStrategy compositeExclusionStrategy = new FireExclusionStrategyComposite(serializationExclusions);
			registerPostProcessor(Object.class, new MethodInvokerPostProcessor<Object>(compositeExclusionStrategy, enableExposeMethodParams,
					enableExposeMethodResults));
        }

        if(enableExclusionByValueStrategies) {
            builder.registerTypeAdapterFactory(new ExcludeByValueTypeAdapterFactory(fieldInspector, factory));
        }

		if (enableExcludeByAnnotation) {
			builder.setExclusionStrategies(new AnnotationExclusionStrategy<Exclude>(Exclude.class));
			builder.addSerializationExclusionStrategy(new AnnotationExclusionStrategy<ExcludeSerialize>(ExcludeSerialize.class));
			builder.addDeserializationExclusionStrategy(new AnnotationExclusionStrategy<ExcludeDeserialize>(ExcludeDeserialize.class));
		}

        for(Class clazz: orderedClasses){
            ClassConfig config = classConfigMap.get(clazz);
            if(config.getTypeSelector() != null) {
                builder.registerTypeAdapterFactory(new TypeSelectorTypeAdapterFactory(config, alreadyResolvedTypeTokensRegistry));
            }
            builder.registerTypeAdapterFactory(new HooksTypeAdapterFactory(config));
        }

        for(Map.Entry<Class, Enum> enumDefault: enumDefaultValues.entrySet()) {
            builder.registerTypeAdapterFactory(new EnumDefaultValueTypeAdapterFactory(enumDefault.getKey(), enumDefault.getValue()));
        }

        if(dateSerializationPolicy != null){
            builder.registerTypeAdapter(Date.class, dateSerializationPolicy.createTypeAdapter(serializeTimeZone));
        }

        builder.registerTypeAdapterFactory(new SimpleIterableTypeAdapterFactory());
        builder.registerTypeAdapterFactory(new WrapTypeAdapterFactory(wrappedClasses));

        return builder;
    }

    /**
     * Returns a new {@link Gson} instance
     * @return
     */
    public Gson createGson(){
        return createGsonBuilder().create();
    }
}
