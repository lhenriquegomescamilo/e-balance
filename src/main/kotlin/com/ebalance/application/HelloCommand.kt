package eg.zaidi.onboarding.com.ebalance.application

import com.github.ajalt.clikt.core.CliktCommand

class HelloCommand : CliktCommand() {
    override fun run() {
        echo("Hello World!")
    }
}