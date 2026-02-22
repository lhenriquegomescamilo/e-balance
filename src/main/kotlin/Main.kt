package eg.zaidi.onboarding

import com.github.ajalt.clikt.core.main
import eg.zaidi.onboarding.com.ebalance.application.HelloCommand

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main(args: Array<String> = emptyArray()) {
    HelloCommand().main(args)
}