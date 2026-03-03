package com.abuhrov.openword.data.config

import com.abuhrov.openword.model.CommentarySource
import com.abuhrov.openword.model.Translation

val availableTranslations = listOf(
    Translation(
        id = "GRM",
        displayName = "Біблія: Вічне Євангеліє",
        fileName = "translations/GRM.SQLite3",
        commentarySource = CommentarySource("Біблія: Вічне Євангеліє", "commentaries/GRM.commentaries.SQLite3")
    ),
    Translation(
        id = "CUV'23",
        displayName = "Сучасний переклад УБТ",
        fileName = "translations/CUV.SQLite3",
        commentarySource = CommentarySource("Сучасний переклад", "commentaries/CUV.commentaries.SQLite3")
    ),
    Translation("HOM", "Іван Хоменко", "translations/HOM.SQLite3"),
    Translation("UKRK", "Куліша, Пулюя та Нечуя-Левицького", "translations/UKRK.SQLite3"),
    Translation("KJV", "King James", "translations/KJV.SQLite3"),
    Translation("МСЦ'22", "МСЦ ЄХБ", "translations/MSC.SQLite3"),
    Translation(
        id = "UBIO",
        displayName = "Іван Огієнко",
        fileName = "translations/UBIO.SQLite3",
        commentarySource = CommentarySource("Іван Огієнко", "commentaries/UBIO.commentaries.SQLite3")
    ),
    Translation("НУП", "Юрій Попченко", "translations/NUP.SQLite3"),
    Translation(
        id = "НПУ'22",
        displayName = "Новий переклад українською",
        fileName = "translations/NPU.SQLite3",
        commentarySource = CommentarySource("Новий переклад українською", "commentaries/NPU.commentaries.SQLite3")
    ),
    Translation(
        id = "UMT",
        displayName = "Свята Біблія: Сучасною мовою",
        fileName = "translations/UMT.SQLite3",
        commentarySource = CommentarySource("Свята Біблія: Сучасною мовою", "commentaries/UMT.commentaries.SQLite3")
    )
)

val availableCommentaries = listOf(
    CommentarySource("Далласька богословська семінарія", "commentaries/dallas.SQLite3"),
    CommentarySource("Біблійний культурно-історичний коментар", "commentaries/IVP.SQLite3"),
    CommentarySource("Томас Ко́нстебл", "commentaries/constable.SQLite3"),
)
