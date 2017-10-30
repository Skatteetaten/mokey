package no.skatteetaten.aurora.mokey.extensions

//Used if you just want to memoize something no matter the input
class Memoize0<out R>(val f: () -> R) : () -> R {
    private val values = mutableMapOf<String, R>()
    override fun invoke(): R {
        return values.getOrPut("memoized", { f() })
    }
}

//Used if you just want to memoize something with 1 input
class Memoize1<in T, out R>(val f: (T) -> R) : (T) -> R {
    private val values = mutableMapOf<T, R>()
    override fun invoke(x: T): R {
        return values.getOrPut(x, { f(x) })
    }
}

fun <T, R> ((T) -> R).memoize(): (T) -> R = Memoize1(this)

fun <R> (() -> R).memoize(): () -> R = Memoize0(this)
