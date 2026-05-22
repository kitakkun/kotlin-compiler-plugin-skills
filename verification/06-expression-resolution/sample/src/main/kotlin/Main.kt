import com.example.Dsl
import com.example.DslContext
import com.example.greet

@DslContext
fun runDsl(d: Dsl) {
    // The very first `println(...)` call acts as the trigger that lets the plugin's
    // `addNewImplicitReceivers` install a `Dsl` implicit receiver into the current
    // body scope. From here on, calls to top-level extension functions on `Dsl`
    // (such as `greet()`) resolve as `Dsl.greet()` without an explicit receiver.
    // The accompanying IR pass rewrites the synthetic-receiver placeholder produced
    // by fir2ir into a real load of the `d` parameter at runtime.
    println("@DslContext entered")
    println(greet())
}

fun main() {
    runDsl(Dsl())
}
