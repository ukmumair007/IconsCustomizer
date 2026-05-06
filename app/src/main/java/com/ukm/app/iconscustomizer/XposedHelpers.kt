package com.ukm.app.iconscustomizer // Replace with your package

import android.util.Log
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.WeakHashMap

/**
 * A complete, pure-reflection port of the classic XposedHelpers for modern Android development.
 */
object XposedHelpers {

    private val additionalFields = WeakHashMap<Any, HashMap<String, Any?>>()
    private val additionalStaticFields = HashMap<Class<*>, HashMap<String, Any?>>()

    private val primitiveToWrapper = mapOf<Class<*>?, Class<*>?>(
        Boolean::class.javaPrimitiveType to Boolean::class.javaObjectType,
        Byte::class.javaPrimitiveType to Byte::class.javaObjectType,
        Char::class.javaPrimitiveType to Character::class.javaObjectType,
        Short::class.javaPrimitiveType to Short::class.javaObjectType,
        Int::class.javaPrimitiveType to Integer::class.javaObjectType,
        Long::class.javaPrimitiveType to Long::class.javaObjectType,
        Float::class.javaPrimitiveType to Float::class.javaObjectType,
        Double::class.javaPrimitiveType to Double::class.javaObjectType
    )

    // =========================================================================
    // CLASS LOOKUP
    // =========================================================================

    fun findClass(className: String, classLoader: ClassLoader? = null): Class<*> {
        return try {
            val className = className.removePrefix("sources/")
                .removeSuffix(".java")
                .replace("/", ".")
            if (classLoader == null) Class.forName(className) else Class.forName(
                className,
                false,
                classLoader
            )
        } catch (e: ClassNotFoundException) {
            throw e
        }
    }

    fun findClassIfExists(className: String, classLoader: ClassLoader? = null): Class<*>? {
        return try {
            findClass(className, classLoader)
        } catch (e: ClassNotFoundException) {
            null
        }
    }

    // =========================================================================
    // FIELD LOOKUP
    // =========================================================================

    fun findField(clazz: Class<*>, fieldName: String): Field {
        var currentClass: Class<*>? = clazz
        while (currentClass != null) {
            try {
                val field = currentClass.getDeclaredField(fieldName)
                field.isAccessible = true
                return field
            } catch (e: NoSuchFieldException) {
            }
            currentClass = currentClass.superclass
        }
        throw NoSuchFieldError("Field $fieldName not found in ${clazz.name}")
    }

    fun findFieldIfExists(clazz: Class<*>, fieldName: String): Field? {
        return try {
            findField(clazz, fieldName)
        } catch (e: NoSuchFieldError) {
            null
        }
    }

    fun findFirstFieldByExactType(clazz: Class<*>, type: Class<*>): Field {
        var currentClass: Class<*>? = clazz
        while (currentClass != null) {
            for (field in currentClass.declaredFields) {
                if (field.type == type) {
                    field.isAccessible = true
                    return field
                }
            }
            currentClass = currentClass.superclass
        }
        throw NoSuchFieldError("Field of type ${type.name} not found in ${clazz.name}")
    }

    // =========================================================================
    // METHOD LOOKUP
    // =========================================================================

    fun findMethodExact(
        clazz: Class<*>,
        methodName: String,
        vararg parameterTypes: Class<*>
    ): Method {
        var currentClass: Class<*>? = clazz
        while (currentClass != null) {
            try {
                val method = currentClass.getDeclaredMethod(methodName, *parameterTypes)
                method.isAccessible = true
                return method
            } catch (e: NoSuchMethodException) {
            }
            currentClass = currentClass.superclass
        }
        throw Exception("Method $methodName not found in ${clazz.name}")
    }

    fun findMethodExactIfExists(
        clazz: Class<*>,
        methodName: String,
        vararg parameterTypes: Class<*>
    ): Method? {
        return try {
            findMethodExact(clazz, methodName, *parameterTypes)
        } catch (e: NoSuchMethodError) {
            null
        }
    }

    fun findMethodBestMatch(
        clazz: Class<*>,
        methodName: String,
        vararg parameterTypes: Class<*>
    ): Method {
        var currentClass: Class<*>? = clazz

        while (currentClass != null) {
            for (method in currentClass.declaredMethods) {
                if (method.name == methodName && method.parameterTypes.size == parameterTypes.size) {
                    var match = true
                    for (i in parameterTypes.indices) {
                        if (!isAssignable(parameterTypes[i], method.parameterTypes[i])) {
                            match = false
                            break
                        }
                    }
                    if (match) {
                        // First match wins in this basic implementation
                        method.isAccessible = true
                        return method
                    }
                }
            }
            currentClass = currentClass.superclass
        }
        throw NoSuchMethodError("Method $methodName with matching params not found in ${clazz.name}")
    }

    // =========================================================================
    // CONSTRUCTOR LOOKUP
    // =========================================================================

    fun findConstructorExact(clazz: Class<*>, vararg parameterTypes: Class<*>): Constructor<*> {
        return try {
            val constructor = clazz.getDeclaredConstructor(*parameterTypes)
            constructor.isAccessible = true
            constructor
        } catch (e: NoSuchMethodException) {
            throw NoSuchMethodError("Constructor not found in ${clazz.name}")
        }
    }

    fun findConstructorExactIfExists(
        clazz: Class<*>,
        vararg parameterTypes: Class<*>
    ): Constructor<*>? {
        return try {
            findConstructorExact(clazz, *parameterTypes)
        } catch (e: NoSuchMethodError) {
            null
        }
    }

    fun findConstructorBestMatch(clazz: Class<*>, vararg parameterTypes: Class<*>): Constructor<*> {
        for (constructor in clazz.declaredConstructors) {
            if (constructor.parameterTypes.size == parameterTypes.size) {
                var match = true
                for (i in parameterTypes.indices) {
                    if (!isAssignable(parameterTypes[i], constructor.parameterTypes[i])) {
                        match = false
                        break
                    }
                }
                if (match) {
                    constructor.isAccessible = true
                    return constructor
                }
            }
        }
        throw NoSuchMethodError("Constructor with matching params not found in ${clazz.name}")
    }

    // =========================================================================
    // INSTANCE FIELD GETTERS & SETTERS
    // =========================================================================

    fun getObjectField(obj: Any, fieldName: String): Any? =
        findField(obj.javaClass, fieldName).get(obj)

    fun setObjectField(obj: Any, fieldName: String, value: Any?) =
        findField(obj.javaClass, fieldName).set(obj, value)

    fun getBooleanField(obj: Any, fieldName: String): Boolean =
        findField(obj.javaClass, fieldName).getBoolean(obj)

    fun setBooleanField(obj: Any, fieldName: String, value: Boolean) =
        findField(obj.javaClass, fieldName).setBoolean(obj, value)

    fun getByteField(obj: Any, fieldName: String): Byte =
        findField(obj.javaClass, fieldName).getByte(obj)

    fun setByteField(obj: Any, fieldName: String, value: Byte) =
        findField(obj.javaClass, fieldName).setByte(obj, value)

    fun getCharField(obj: Any, fieldName: String): Char =
        findField(obj.javaClass, fieldName).getChar(obj)

    fun setCharField(obj: Any, fieldName: String, value: Char) =
        findField(obj.javaClass, fieldName).setChar(obj, value)

    fun getDoubleField(obj: Any, fieldName: String): Double =
        findField(obj.javaClass, fieldName).getDouble(obj)

    fun setDoubleField(obj: Any, fieldName: String, value: Double) =
        findField(obj.javaClass, fieldName).setDouble(obj, value)

    fun getFloatField(obj: Any, fieldName: String): Float =
        findField(obj.javaClass, fieldName).getFloat(obj)

    fun setFloatField(obj: Any, fieldName: String, value: Float) =
        findField(obj.javaClass, fieldName).setFloat(obj, value)

    fun getIntField(obj: Any, fieldName: String): Int =
        findField(obj.javaClass, fieldName).getInt(obj)

    fun setIntField(obj: Any, fieldName: String, value: Int) =
        findField(obj.javaClass, fieldName).setInt(obj, value)

    fun getLongField(obj: Any, fieldName: String): Long =
        findField(obj.javaClass, fieldName).getLong(obj)

    fun setLongField(obj: Any, fieldName: String, value: Long) =
        findField(obj.javaClass, fieldName).setLong(obj, value)

    fun getShortField(obj: Any, fieldName: String): Short =
        findField(obj.javaClass, fieldName).getShort(obj)

    fun setShortField(obj: Any, fieldName: String, value: Short) =
        findField(obj.javaClass, fieldName).setShort(obj, value)

    fun getSurroundingThis(obj: Any): Any? = getObjectField(obj, "this$0")

    // =========================================================================
    // STATIC FIELD GETTERS & SETTERS
    // =========================================================================

    fun getStaticObjectField(clazz: Class<*>, fieldName: String): Any? =
        findField(clazz, fieldName).get(null)

    fun setStaticObjectField(clazz: Class<*>, fieldName: String, value: Any?) =
        findField(clazz, fieldName).set(null, value)

    fun getStaticBooleanField(clazz: Class<*>, fieldName: String): Boolean =
        findField(clazz, fieldName).getBoolean(null)

    fun setStaticBooleanField(clazz: Class<*>, fieldName: String, value: Boolean) =
        findField(clazz, fieldName).setBoolean(null, value)

    fun getStaticByteField(clazz: Class<*>, fieldName: String): Byte =
        findField(clazz, fieldName).getByte(null)

    fun setStaticByteField(clazz: Class<*>, fieldName: String, value: Byte) =
        findField(clazz, fieldName).setByte(null, value)

    fun getStaticCharField(clazz: Class<*>, fieldName: String): Char =
        findField(clazz, fieldName).getChar(null)

    fun setStaticCharField(clazz: Class<*>, fieldName: String, value: Char) =
        findField(clazz, fieldName).setChar(null, value)

    fun getStaticDoubleField(clazz: Class<*>, fieldName: String): Double =
        findField(clazz, fieldName).getDouble(null)

    fun setStaticDoubleField(clazz: Class<*>, fieldName: String, value: Double) =
        findField(clazz, fieldName).setDouble(null, value)

    fun getStaticFloatField(clazz: Class<*>, fieldName: String): Float =
        findField(clazz, fieldName).getFloat(null)

    fun setStaticFloatField(clazz: Class<*>, fieldName: String, value: Float) =
        findField(clazz, fieldName).setFloat(null, value)

    fun getStaticIntField(clazz: Class<*>, fieldName: String): Int =
        findField(clazz, fieldName).getInt(null)

    fun setStaticIntField(clazz: Class<*>, fieldName: String, value: Int) =
        findField(clazz, fieldName).setInt(null, value)

    fun getStaticLongField(clazz: Class<*>, fieldName: String): Long =
        findField(clazz, fieldName).getLong(null)

    fun setStaticLongField(clazz: Class<*>, fieldName: String, value: Long) =
        findField(clazz, fieldName).setLong(null, value)

    fun getStaticShortField(clazz: Class<*>, fieldName: String): Short =
        findField(clazz, fieldName).getShort(null)

    fun setStaticShortField(clazz: Class<*>, fieldName: String, value: Short) =
        findField(clazz, fieldName).setShort(null, value)

    // =========================================================================
    // METHOD EXECUTION & INSTANTIATION
    // =========================================================================

    fun callMethod(
        obj: Any,
        methodName: String,
        parameterTypes: Array<Class<*>> = emptyArray(),
        vararg args: Any?
    ): Any? {
        val method = try {
            findMethodExact(obj.javaClass, methodName, *parameterTypes)
        } catch (e: NoSuchMethodError) {
            try {
                findMethodBestMatch(obj.javaClass, methodName, *parameterTypes)
            } catch (e: NoSuchMethodError) {
                Log.e(MainHook.TAG, "callMethod: ", e)
                throw Exception("Method $methodName with matching params not found in ${obj.javaClass.name}")
            }
        }
        return method.invoke(obj, *args)
    }

    fun callStaticMethod(
        clazz: Class<*>,
        methodName: String,
        parameterTypes: Array<Class<*>> = emptyArray(),
        vararg args: Any?
    ): Any? {

        val method = try {
            findMethodExact(clazz, methodName, *parameterTypes)
        } catch (e: NoSuchMethodError) {
            findMethodBestMatch(clazz, methodName, *parameterTypes)
        }

        return if (args.isEmpty()) {
            method.invoke(null)
        } else {
            method.invoke(null, *args)
        }
    }

    fun newInstance(
        clazz: Class<*>,
        parameterTypes: Array<Class<*>> = emptyArray(),
        vararg args: Any?
    ): Any {

        val constructor = try {
            findConstructorExact(clazz, *parameterTypes)
        } catch (e: NoSuchMethodError) {
            findConstructorBestMatch(clazz, *parameterTypes)
        }

        return if (args.isEmpty()) {
            constructor.newInstance()
        } else {
            constructor.newInstance(*args)
        }
    }

    // =========================================================================
    // ADDITIONAL FIELDS (Virtual Data Storage)
    // =========================================================================

    fun getAdditionalInstanceField(obj: Any, key: String): Any? = additionalFields[obj]?.get(key)

    fun setAdditionalInstanceField(obj: Any, key: String, value: Any?) {
        additionalFields.getOrPut(obj) { HashMap() }[key] = value
    }

    fun removeAdditionalInstanceField(obj: Any, key: String): Any? =
        additionalFields[obj]?.remove(key)

    fun getAdditionalStaticField(clazz: Class<*>, key: String): Any? =
        additionalStaticFields[clazz]?.get(key)

    fun setAdditionalStaticField(clazz: Class<*>, key: String, value: Any?) {
        additionalStaticFields.getOrPut(clazz) { HashMap() }[key] = value
    }

    fun removeAdditionalStaticField(clazz: Class<*>, key: String): Any? =
        additionalStaticFields[clazz]?.remove(key)

    // =========================================================================
    // INTERNAL UTILS
    // =========================================================================


    private fun isAssignable(provided: Class<*>, target: Class<*>): Boolean {
        if (target.isAssignableFrom(provided)) return true

        // Handle primitive auto-boxing logic (e.g., passing 'int' where 'Integer' is expected, or vice versa)
        val providedWrapper = if (provided.isPrimitive) primitiveToWrapper[provided] else provided
        val targetWrapper = if (target.isPrimitive) primitiveToWrapper[target] else target

        return targetWrapper != null && providedWrapper != null && targetWrapper.isAssignableFrom(
            providedWrapper
        )
    }
}