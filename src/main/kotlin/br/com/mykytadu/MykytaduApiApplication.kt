package br.com.mykytadu

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MykytaduApiApplication

fun main(args: Array<String>) {
    runApplication<MykytaduApiApplication>(*args)
}
