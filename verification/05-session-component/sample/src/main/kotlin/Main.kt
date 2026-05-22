import com.example.Tracked

@Tracked
class Base {
    fun greet() = "Base"
}

// Without the TrackedOpener (which consults the shared session component),
// this would fail to compile because `Base` is final by default.
class Sub : Base()

fun main() {
    val s: Base = Sub()
    println(s.greet())
}
