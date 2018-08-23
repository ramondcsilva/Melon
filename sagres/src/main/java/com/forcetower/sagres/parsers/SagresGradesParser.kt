/*
 * Copyright (c) 2018.
 *
 * This file is part of the UNES Open Source Project.
 *
 * UNES is licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.forcetower.sagres.parsers

import com.forcetower.sagres.database.model.CourseVariant
import com.forcetower.sagres.database.model.Grade
import com.forcetower.sagres.database.model.GradeInfo
import org.jsoup.nodes.Document
import timber.log.Timber

object SagresGradesParser {
    @JvmStatic
    fun extractSemesterCodes(document: Document): List<Pair<Long, String>> {
        val list: MutableList<Pair<Long, String>> = ArrayList()
        val semestersValues = document.selectFirst("select[id=\"ctl00_MasterPlaceHolder_ddPeriodosLetivos_ddPeriodosLetivos\"]")
        if (semestersValues != null) {
            val options = semestersValues.select("option")
            for (option in options) {
                val value = option.attr("value").trim()
                val semester = option.text().trim()
                try {
                    val semesterId = value.toLong()
                    val pair = Pair(semesterId, semester)
                    list.add(pair)
                } catch (e: Exception) {
                    Timber.d("Can't parse long: $value")
                }
            }
            return list
        } else {
            //TODO place crashlytics here
            return list
        }
    }

    @JvmStatic
    fun getSelectedSemester(document: Document): Pair<Boolean, Long>? {
        val values = document.select("option[selected=\"selected\"]")
        return if (values.size == 1) {
            val value = values[0].attr("value").trim()
            try {
                val id = value.toLong()
                Pair(true, id)
            } catch (e: Exception) {
                Timber.d("Can't parse long: $value")
                null
            }
        } else {
            val defValue = document.selectFirst("select[id=\"ctl00_MasterPlaceHolder_ddPeriodosLetivos_ddPeriodosLetivos\"]")
            if (defValue != null) {
                val selected = defValue.selectFirst("option[selected=\"selected\"]")
                if (selected != null) {
                    val value = selected.attr("value").trim()
                    try {
                        val id = value.toLong()
                        Timber.d("Successfully found current semester using the alternate way")
                        Pair(false, id)
                    } catch (e: Exception) {
                        Timber.d("Can't parse long: $value")
                    }
                }
            }
            null
        }
    }

    @JvmStatic
    fun extractCourseVariants(document: Document): List<CourseVariant> {
        val courses: MutableList<CourseVariant> = ArrayList()
        val variants = document.selectFirst("select[id=\"ctl00_MasterPlaceHolder_ddRegistroCurso\"]")
        if (variants != null) {
            val elements = variants.children()
            for (element in elements) {
                try {
                    val uefsId = element.attr("value").toLong()
                    val name   = element.text().trim()
                    courses.add(CourseVariant(uefsId, name))
                    Timber.d("Added variant id: $uefsId with name: $name")
                } catch (e: Exception) {}
            }
        }

        return courses
    }

    @JvmStatic
    fun canExtractGrades(document: Document): Boolean {
        val bulletin = document.selectFirst("div[id\"divBoletins\"]")
        return if (bulletin != null) {
            bulletin.selectFirst("div[class=\"boletim-container\"]") != null
        } else {
            false
        }
    }

    @JvmStatic
    fun extractGrades(document: Document): List<Grade> {
        val grades: MutableList<Grade> = ArrayList()
        val bulletin = document.selectFirst("div[id\"divBoletins\"]")
        val classes  = document.select("div[class=\"boletim-container\"]")

        for (clazz in classes) {
            try {
                val info = clazz.selectFirst("div[class=\"boletim-item-info\"]")
                val name = info.selectFirst("span[class=\"boletim-item-titulo cor-destaque\"]")

                val discipline = name.text().trim()
                val grade = Grade(discipline)

                val gradeInfo = clazz.selectFirst("div[class=\"boletim-notas\"]")
                val table = gradeInfo.selectFirst("table")
                val body  = table.selectFirst("tbody")

                if (body != null) {
                    val trs = body.select("tr")
                    for (tr in trs) {
                        val children = tr.children()
                        if (children.size == 4) {
                            val td = children[0]
                            if (td.children().size == 0) {
                                val mean = children[2]
                                grade.partialMean = mean.text().trim()
                            } else {
                                val date = children[0].text().trim()
                                val evaluation = children[1].text().trim()
                                val score = children[2].text().trim()
                                var weight = children[3].text().trim().toDoubleOrNull()
                                if (weight == null) weight = 1.0
                                grade.addInfo(GradeInfo(evaluation, score, date, weight))
                            }
                        }
                    }
                } else {

                }
            } catch (t: Throwable) {
                Timber.d("Exception happened")
                t.printStackTrace()
            }
        }

        return grades
    }
}