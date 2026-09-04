package br.com.mykytadu

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.modulith.Modulithic

@Modulithic(systemName = "MykytaDu API", sharedModules = ["shared"])
@SpringBootApplication
class MykytaduApiApplication

fun main(args: Array<String>) {
    runApplication<MykytaduApiApplication>(*args)
}
