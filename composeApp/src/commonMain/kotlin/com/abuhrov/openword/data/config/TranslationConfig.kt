package com.abuhrov.openword.data.config

import com.abuhrov.openword.model.CommentarySource
import com.abuhrov.openword.model.Translation

val availableTranslations = listOf(
    Translation(
        id = "CUV",
        displayName = "Сучасний переклад УБТ",
        fileName = "translations/CUV.SQLite3",
        commentarySource = CommentarySource("Сучасний переклад", "commentaries/CUV.commentaries.SQLite3")
    ),
    Translation("GYZ", "Олександр Гижа", "translations/GYZ.SQLite3"),
    Translation("HOM", "Іван Хоменко", "translations/HOM.SQLite3"),
    Translation("KJV", "King James", "translations/KJV.SQLite3"),
    Translation("МСЦ", "МСЦ ЄХБ", "translations/MSC.SQLite3"),
    Translation(
        id = "UBIO",
        displayName = "Іван Огієнко",
        fileName = "translations/UBIO.SQLite3",
        commentarySource = CommentarySource("Іван Огієнко", "commentaries/UBIO.commentaries.SQLite3")
    ),
    Translation("NUP", "Юрій Попченко", "translations/NUP.SQLite3"),
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
