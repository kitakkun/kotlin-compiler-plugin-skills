import com.example.MyKind

fun acceptMyKind(block: @MyKind () -> Unit) {
    block()
}

fun acceptPlain(block: () -> Unit) {
    block()
}

fun main() {
    acceptMyKind @MyKind { println("MyKind") }
}
