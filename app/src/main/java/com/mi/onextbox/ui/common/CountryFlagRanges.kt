package com.mi.onextbox.ui.common

internal data class CountryFlagRange(val start: Int, val end: Int)

private val supportedFlagCountries = (
    "AC AD AE AF AG AI AL AM AO AQ AR AS AT AU AW AX AZ BA BB BD BE BF BG BH BI BJ BL BM BN BO BQ BR BS BT BV BW BY BZ " +
        "CA CC CD CF CG CH CI CK CL CM CN CO CP CQ CR CU CV CW CX CY CZ DE DG DJ DK DM DO DZ EA EC EE EG EH ER ES ET EU " +
        "FI FJ FK FM FO FR GA GB GD GE GF GG GH GI GL GM GN GP GQ GR GS GT GU GW GY HK HM HN HR HT HU IC ID IE IL IM IN IO IQ IR IS IT " +
        "JE JM JO JP KE KG KH KI KM KN KP KR KW KY KZ LA LB LC LI LK LR LS LT LU LV LY MA MC MD ME MF MG MH MK ML MM MN MO MP MQ MR MS MT MU MV MW MX MY MZ " +
        "NA NC NE NF NG NI NL NO NP NR NU NZ OM PA PE PF PG PH PK PL PM PN PR PS PT PW PY QA RE RO RS RU RW SA SB SC SD SE SG SH SI SJ SK SL SM SN SO SR SS ST SV SX SY SZ " +
        "TA TC TD TF TG TH TJ TK TL TM TN TO TR TT TV TW TZ UA UG UM UN US UY UZ VA VC VE VG VI VN VU WF WS XK YE YT ZA ZM ZW"
    ).split(' ').toSet()

internal fun countryFlagRanges(text: String): List<CountryFlagRange> = buildList {
    var index = 0
    while (index < text.length) {
        val first = text.codePointAt(index)
        val secondIndex = index + Character.charCount(first)
        if (first in 0x1F1E6..0x1F1FF && secondIndex < text.length) {
            val second = text.codePointAt(secondIndex)
            if (second in 0x1F1E6..0x1F1FF) {
                val code = "${'A' + (first - 0x1F1E6)}${'A' + (second - 0x1F1E6)}"
                val end = secondIndex + Character.charCount(second)
                if (code in supportedFlagCountries) add(CountryFlagRange(index, end))
                index = end
                continue
            }
        }
        index = secondIndex
    }
}
