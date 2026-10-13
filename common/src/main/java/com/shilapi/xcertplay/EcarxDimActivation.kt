package com.shilapi.xcertplay

import android.content.Context
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Turns the head unit's instrument projection on through ECARX's XUI AdaptApi.
 *
 * The Geely E01 exposes its cluster as an ordinary second display, so DiPlay can draw CarPlay's
 * cluster stream there with a plain [ClusterMapPresentation] — but the head unit only routes that
 * display to the instrument while its own navigation projection is on, and that switch lives in
 * the vendor stack: `DimInteraction.setPresentationToDimSwitch(...)`.
 *
 * A third-party app reaches it only with `android.car.permission.CAR_VENDOR_EXTENSION`
 * (`signature|system`), which requires a privileged (system-image) install or a platform
 * signature: without it `DimInteraction.create(context)` returns null. The call chain below is the
 * one recovered from the on-car probe and cross-checked against the E02 module's hook targets.
 *
 * Everything here is best-effort and diagnostic: on any failure it logs the exact stage and
 * returns false so the caller can fall back (for example to the semi-automatic route).
 *
 * Deliberately no Android framework classes beyond Context: the whole thing is reflection, so it
 * compiles and runs on every build type even though the ECARX classes are absent at compile time.
 */
internal object EcarxDimActivation {
    private const val TAG = "DiPlay-Cluster"

    private const val DIM_CLASS = "com.ecarx.xui.adaptapi.diminteraction.DimInteraction"
    private const val CONNECTABLE_CLASS = "com.ecarx.xui.adaptapi.binder.IConnectable"
    private const val WATCHER_CLASS = "com.ecarx.xui.adaptapi.binder.IConnectable\$IConnectWatcher"
    private const val SET_PRESENTATION = "setPresentationToDimSwitch"
    private const val CREATED = "create"
    private const val REGISTER_WATCHER = "registerConnectWatcher"
    private const val CONNECT = "connect"
    private const val ON_CONNECTED = "onConnected"

    /**
     * `mode` for `setPresentationToDimSwitch`. 1 is the value both the on-car probe and the E02
     * module use for "project this presentation onto the instrument".
     */
    private const val MODE_ON = 1

    /** One activation attempt per process; the vendor connection is process-wide anyway. */
    private val attempted = AtomicBoolean(false)

    @Volatile private var dim: Any? = null

    /** True once the vendor API object was obtained (i.e. the permission is actually granted). */
    val available: Boolean get() = dim != null

    /**
     * Ensures the instrument projection is switched on for [context]'s package.
     *
     * Idempotent: the first call performs the (async) connect; the switch itself is thrown from the
     * connect callback. Returns true only when the API object was obtained and the connect call was
     * issued — a `false` means the caller should use its fallback, and [log] says why.
     */
    fun activate(context: Context, log: (String) -> Unit): Boolean {
        dim?.let {
            // Already connected in this process: just re-assert the switch.
            return setPresentation(it, context.packageName, true, log)
        }
        if (!attempted.compareAndSet(false, true)) return false

        val instance = createInstance(context, log) ?: return false
        dim = instance

        val watcher = newWatcher(instance, context, log) ?: return false
        return connect(instance, watcher, log)
    }

    /** Releases the vendor connection if one was made (best effort). */
    fun release(log: (String) -> Unit) {
        val current = dim ?: return
        runCatching {
            val connectable = Class.forName(CONNECTABLE_CLASS)
            connectable.getMethod("disConnect").invoke(current)
            log("EcarxDim: disconnected")
        }.onFailure { log("EcarxDim: disconnect failed ${it.javaClass.simpleName}") }
        dim = null
        attempted.set(false)
    }

    private fun createInstance(context: Context, log: (String) -> Unit): Any? {
        val cls = try {
            Class.forName(DIM_CLASS)
        } catch (error: Throwable) {
            log("EcarxDim: ${DIM_CLASS} not present (${error.javaClass.simpleName})")
            return null
        }
        val created = try {
            cls.getMethod(CREATED, Context::class.java).invoke(null, context)
        } catch (error: Throwable) {
            log("EcarxDim: create() failed (${error.javaClass.simpleName}: ${error.message})")
            return null
        }
        if (created == null) {
            // The expected case without CAR_VENDOR_EXTENSION (signature|system).
            log("EcarxDim: create() returned null — permission CAR_VENDOR_EXTENSION missing?")
            return null
        }
        log("EcarxDim: create() -> ${created.javaClass.name}")
        return created
    }

    private fun newWatcher(instance: Any, context: Context, log: (String) -> Unit): Any? {
        val watcherInterface = try {
            Class.forName(WATCHER_CLASS)
        } catch (error: Throwable) {
            log("EcarxDim: ${WATCHER_CLASS} not present (${error.javaClass.simpleName})")
            return null
        }
        val handler = InvocationHandler { _, method: Method, _ ->
            if (method.name == ON_CONNECTED) {
                log("EcarxDim: onConnected — switching presentation to the instrument")
                setPresentation(instance, context.packageName, true, log)
            }
            defaultValue(method.returnType)
        }
        return try {
            Proxy.newProxyInstance(watcherInterface.classLoader, arrayOf(watcherInterface), handler)
        } catch (error: Throwable) {
            log("EcarxDim: watcher proxy failed (${error.javaClass.simpleName})")
            null
        }
    }

    private fun connect(instance: Any, watcher: Any, log: (String) -> Unit): Boolean = try {
        val connectable = Class.forName(CONNECTABLE_CLASS)
        val watcherCls = Class.forName(WATCHER_CLASS)
        connectable.getMethod(REGISTER_WATCHER, watcherCls).invoke(instance, watcher)
        connectable.getMethod(CONNECT).invoke(instance)
        log("EcarxDim: connect() issued; waiting for onConnected")
        true
    } catch (error: Throwable) {
        log("EcarxDim: connect failed (${error.javaClass.simpleName}: ${error.message})")
        false
    }

    private fun setPresentation(instance: Any, packageName: String, enabled: Boolean, log: (String) -> Unit): Boolean = try {
        val method = instance.javaClass.getMethod(
            SET_PRESENTATION,
            Int::class.javaPrimitiveType,
            String::class.java,
            String::class.java,
            Boolean::class.javaPrimitiveType,
        )
        method.invoke(instance, MODE_ON, packageName, packageName, enabled)
        log("EcarxDim: setPresentationToDimSwitch($MODE_ON, $packageName, $packageName, $enabled) ok")
        true
    } catch (error: Throwable) {
        log("EcarxDim: $SET_PRESENTATION failed (${error.javaClass.simpleName}: ${error.message})")
        false
    }

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Boolean.TYPE -> false
        java.lang.Float.TYPE -> 0f
        java.lang.Double.TYPE -> 0.0
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Character.TYPE -> 0.toChar()
        else -> null
    }
}
