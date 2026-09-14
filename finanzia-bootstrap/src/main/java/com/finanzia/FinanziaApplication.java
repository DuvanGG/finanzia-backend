package com.finanzia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada de la aplicación FinanzIA.
 * Escanea todos los paquetes de com.finanzia para encontrar componentes.
 */
@SpringBootApplication(scanBasePackages = "com.finanzia")
public class FinanziaApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinanziaApplication.class, args);
    }
}
