import com.example.Hello

@Hello
fun greeted() {
    println("hello from inside greeted()")
}

fun main() {
    println("--- main start ---")
    greeted()
    println("--- main end ---")
}
