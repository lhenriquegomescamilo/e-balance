package com.ebalance.domain.model

import org.slf4j.LoggerFactory

/**
 * Enum representing predefined expense/income categories.
 * These correspond to the category table in the database.
 */
enum class Category(val id: Long, val displayName: String) {
    DESCONHECIDA(0, "Desconhecida"),
    MORADIA(1, "Moradia"),
    RESTAURANTE(2, "Restaurante"),
    BISCOITOS(3, "Biscoitos"),
    SUPERMERCADO(4, "Supermercado"),
    ESCOLA_DOS_FILHOS(5, "Escola dos filhos"),
    DELIVERY(6, "Delivery"),
    ROUPA(7, "Roupa"),
    ELETRONICOS(8, "Eletrônicos"),
    CABELO(9, "Cabelo"),
    TRANSPORTE(10, "Transporte"),
    CARRO(11, "Carro"),
    SEGURANCA_SOCIAL(12, "Segurança Social"),
    IRS(13, "IRS"),
    SALARIO(14, "Salário"),
    CONTABILISTA(15, "Contabilista"),
    ARRENDAMENTO(16, "Arrendamento"),
    TELEFONIA(17, "Telefonia"),
    ENERGIA(18, "Energia"),
    AGUA(19, "Água"),
    PERSONAL_TRAINER(20, "Personal Trainer"),
    BARBEIRO(21, "Barbeiro"),
    VIA_VERDE(22, "Via Verde"),
    SEGURO_DE_CARRO(23, "Seguro de carro"),
    DOACOES(24, "Doações"),
    ASSINATURAS(25, "Assinaturas"),
    TAXA_BANCARIA(26, "Taxa Bancária"),
    TURISMO(27, "Turismo"),
    PORTAGEM(28, "Portagem"),
    LAZER(29, "Lazer"),
    CARTAO(30, "Cartão"),
    ESTETICA(31, "Estética"),
    INVESTIMENTO(32, "Investimento"),
    FARMACIA(33, "Farmácia"),
    LEVANTAMENTO(34, "Levantamento"),
    LAVANDERIA(35, "Lavanderia"),
    COMBUSTIVEL(36, "Combustível"),
    HOSPITAL(37, "Hospital"),
    PRESENTE(38, "Presente"),
    LAVA_RAPIDO(39, "Lava Rápido"),
    IMPOSTO(40, "Imposto"),
    EDUCACAO(41, "Educação"),
    SEGURO(42, "Seguro"),
    TRANSFERENCIAS_BRASIL(43, "Transferências Brasil"),
    GINASIO(44, "Ginásio");

    companion object {

        private val log = LoggerFactory.getLogger(Category::class.java)
        /**
         * Returns the category by its ID.
         */
        fun fromId(id: Long): Category =
            entries.find { it.id == id } ?: DESCONHECIDA

        /**
         * Returns the category by its display name.
         */
        fun fromDisplayName(name: String): Category =
            entries.find { it.displayName.equals(name, ignoreCase = true) } ?: DESCONHECIDA

        /**
         * Returns the category by its enum name.
         */
        fun fromEnumName(name: String): Category {
            log.info("Looking up category by enum name: $name")
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: DESCONHECIDA
        }

        /**
         * Returns true if this category is a fixed expense.
         */
        fun isFixedExpense(category: Category): Boolean = when (category) {
            MORADIA, ESCOLA_DOS_FILHOS, CONTABILISTA, ARRENDAMENTO,
            TELEFONIA, ENERGIA, AGUA, PERSONAL_TRAINER, VIA_VERDE,
            SEGURO_DE_CARRO, ASSINATURAS, TAXA_BANCARIA, SEGURO,
            SEGURANCA_SOCIAL, IRS, SALARIO, GINASIO, CARTAO -> true

            else -> false
        }
    }
}
/**
 */