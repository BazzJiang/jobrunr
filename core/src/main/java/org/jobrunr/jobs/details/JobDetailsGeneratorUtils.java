package org.jobrunr.jobs.details;

import org.jobrunr.JobRunrError;
import org.jobrunr.utils.reflection.ReflectionUtils;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jobrunr.JobRunrException.invalidLambdaException;
import static org.jobrunr.utils.diagnostics.DiagnosticsBuilder.diagnostics;
import static org.jobrunr.utils.reflection.ReflectionUtils.getField;
import static org.jobrunr.utils.reflection.ReflectionUtils.getMethod;
import static org.jobrunr.utils.reflection.ReflectionUtils.loadClass;
import static org.jobrunr.utils.reflection.ReflectionUtils.toClass;

public class JobDetailsGeneratorUtils {

    private JobDetailsGeneratorUtils() {
    }

    public static String toFQClassName(String byteCodeName) {
        return byteCodeName.replace("/", ".");
    }

    public static String toFQResource(String byteCodeName) {
        return byteCodeName.replace(".", "/");
    }

    public static InputStream getClassContainingLambdaAsInputStream(Object lambda) {
        String location = getClassLocationOfLambda(lambda);
        return lambda.getClass().getResourceAsStream(location);
    }

    public static String getClassLocationOfLambda(Object lambda) {
        String name = lambda.getClass().getName();
        return "/" + toFQResource(name.substring(0, name.indexOf("$$"))) + ".class";
    }

    public static Object createObjectViaConstructor(String fqClassName, Class<?>[] parameterTypes, Object[] parameters) {
        try {
            Class<?> clazz = loadClass(fqClassName);
            Constructor<?> constructor = clazz.getDeclaredConstructor(parameterTypes);
            return constructor.newInstance(parameters);
        } catch (Exception e) {
            throw JobRunrError.shouldNotHappenError(
                    "Error creating object via constructor",
                    diagnostics()
                            .with("fqClassName", fqClassName)
                            .withParameterTypes(parameterTypes)
                            .withParameters(parameters),
                    e);
        }
    }

    public static Object createObjectViaMethod(Object objectWithMethodToInvoke, String methodName, Class<?>[] parameterTypes, Object[] parameters) {
        try {
            Class<?> clazz = objectWithMethodToInvoke.getClass();
            Method method = getMethod(clazz, methodName, parameterTypes);
            return method.invoke(objectWithMethodToInvoke, parameters);
        } catch (Exception e) {
            throw JobRunrError.shouldNotHappenError(
                    "Error creating object via method",
                    diagnostics()
                            .with("objectWithMethodToInvoke", objectWithMethodToInvoke.toString() + "(" + objectWithMethodToInvoke.getClass().getName() + ")")
                            .with("methodName", methodName)
                            .withParameterTypes(parameterTypes)
                            .withParameters(parameters),
                    e);
        }
    }

    public static Object createObjectViaStaticMethod(String fqClassName, String methodName, Class<?>[] parameterTypes, Object[] parameters) {
        try {
            Class<?> clazz = loadClass(fqClassName);
            Method method = getMethod(clazz, methodName, parameterTypes);
            return method.invoke(null, parameters);
        } catch (Exception e) {
            throw JobRunrError.shouldNotHappenError(
                    "Error creating object via static method",
                    diagnostics()
                            .with("fqClassName", fqClassName)
                            .with("methodName", methodName)
                            .withParameterTypes(parameterTypes)
                            .withParameters(parameters),
                    e);
        }
    }

    public static Object getObjectViaStaticField(String fqClassName, String fieldName) {
        try {
            Class<?> clazz = loadClass(fqClassName);
            Field field = getField(clazz, fieldName);
            ReflectionUtils.makeAccessible(field);
            return field.get(null);
        } catch (Exception e) {
            throw JobRunrError.shouldNotHappenError(
                    "Error creating object via static field",
                    diagnostics()
                            .with("fqClassName", fqClassName)
                            .with("fieldName", fieldName),
                    e);
        }
    }

    public static Object getObjectViaField(Object object, String fieldName) {
        try {
            Class<?> clazz = object.getClass();
            Field field = getField(clazz, fieldName);
            ReflectionUtils.makeAccessible(field);
            return field.get(object);
        } catch (Exception e) {
            throw JobRunrError.shouldNotHappenError(
                    "Error creating object via field",
                    diagnostics()
                            .withObject(object)
                            .with("fieldName", fieldName),
                    e);
        }
    }

    public static Class<?>[] findParamTypesFromDescriptorAsArray(String desc) {
        return findParamTypesFromDescriptor(desc).toArray(new Class[0]);
    }

    public static List<Class<?>> findParamTypesFromDescriptor(String desc) {
        int beginIndex = desc.indexOf('(');
        int endIndex = desc.lastIndexOf(')');

        if ((beginIndex == -1 && endIndex != -1) || (beginIndex != -1 && endIndex == -1)) {
            throw new IllegalArgumentException("Could not find the parameterTypes in the descriptor " + desc);
        }
        String x0;
        if (beginIndex == -1 && endIndex == -1) {
            x0 = desc;
        } else {
            x0 = desc.substring(beginIndex + 1, endIndex);
        }
        Pattern pattern = Pattern.compile("\\[*L[^;]+;|\\[[ZBCSIFDJ]|[ZBCSIFDJ]"); //Regex for desc \[*L[^;]+;|\[[ZBCSIFDJ]|[ZBCSIFDJ]
        Matcher matcher = pattern.matcher(x0);
        List<Class<?>> paramTypes = new ArrayList<>();
        while (matcher.find()) {
            String paramType = matcher.group();
            Class<?> clazzToAdd = getClassToAdd(paramType);
            paramTypes.add(clazzToAdd);
        }
        return paramTypes;
    }

    private static Class<?> getClassToAdd(String paramType) {
        if ("Z".equals(paramType)) return boolean.class;
        else if ("I".equals(paramType)) return int.class;
        else if ("J".equals(paramType)) return long.class;
        else if ("F".equals(paramType)) return float.class;
        else if ("D".equals(paramType)) return double.class;
        else if ("B".equals(paramType) || "S".equals(paramType) || "C".equals(paramType))
            throw invalidLambdaException(new IllegalArgumentException("Parameters of type byte, short and char are not supported currently."));
        else if (paramType.startsWith("L")) return toClass(toFQClassName(paramType.substring(1).replace(";", "")));
        else if (paramType.startsWith("[")) return toClass(toFQClassName(paramType));
        else throw new IllegalArgumentException("A classType was found which is not known: " + paramType);
    }
}
