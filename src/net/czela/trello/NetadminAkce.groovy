package net.czela.trello

class NetadminAkce {
    Long id = null
    String name = null
    BigDecimal budget = new BigDecimal("0")
    boolean aproved = false
    Long assignedTo = null
    Long activityType = null
    String wikiText

    boolean isValid() {
        return aproved && name != null && activityType != null &&
                assignedTo != null && assignedTo > 0L &&
                budget != null && budget.doubleValue() >= 0.0 && budget.doubleValue() < 2_000_000D
    }

    String getValidationStatus() {
        StringBuilder sb = new StringBuilder()
        if (name == null) sb.append(" Akce musi mit nazev!)")
        if (!aproved) sb.append(" Akce '${name}' neni schvalena!")
        if (activityType == null) sb.append(" Akce '${name}' musi mit nastaven typ!")
        if (assignedTo == null || assignedTo <= 0L) sb.append(" Akce '${name}' musi mit prirazeneho sefa!")
        if (budget == null || budget.doubleValue() < 0.0) sb.append(" Akce '${name}' musi mit kladny rozpocet!")
        if (budget != null && budget.doubleValue() > 2_000_000D) sb.append(" Rozpocet akce '${name}' je neuveritelne vysoky!")
        return sb.toString()
    }
}
