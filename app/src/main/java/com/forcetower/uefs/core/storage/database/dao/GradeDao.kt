/*
 * Copyright (c) 2018.
 * João Paulo Sena <joaopaulo761@gmail.com>
 *
 * This file is part of the UNES Open Source Project.
 *
 * UNES is licensed under the MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.forcetower.uefs.core.storage.database.dao

import androidx.room.*
import androidx.room.OnConflictStrategy.IGNORE
import androidx.room.OnConflictStrategy.REPLACE
import com.forcetower.sagres.database.model.SGrade
import com.forcetower.sagres.database.model.SGradeInfo
import com.forcetower.uefs.core.model.unes.ClassGroup
import com.forcetower.uefs.core.model.unes.ClassStudent
import com.forcetower.uefs.core.model.unes.Grade
import com.forcetower.uefs.core.model.unes.Profile
import com.forcetower.uefs.core.storage.database.accessors.GradeWithClassStudent
import timber.log.Timber

@Dao
abstract class GradeDao {
    @Insert(onConflict = IGNORE)
    abstract fun insert(grade: Grade): Long

    @Update(onConflict = IGNORE)
    abstract fun update(grade: Grade)

    @Query("SELECT * FROM Grade")
    abstract fun getAllGradesDirect(): List<Grade>

    @Transaction
    @Query("SELECT * FROM Grade WHERE notified = 1")
    abstract fun getCreatedGradesDirect(): List<GradeWithClassStudent>

    @Transaction
    @Query("SELECT * FROM Grade WHERE notified = 2")
    abstract fun getDateChangedGradesDirect(): List<GradeWithClassStudent>

    @Transaction
    @Query("SELECT * FROM Grade WHERE notified = 3")
    abstract fun getPostedGradesDirect(): List<GradeWithClassStudent>

    @Transaction
    @Query("SELECT * FROM Grade WHERE notified = 4")
    abstract fun getChangedGradesDirect(): List<GradeWithClassStudent>

    @Query("UPDATE Grade SET notified = 0")
    abstract fun markAllNotified()

    @Transaction
    open fun putGrades(grades: List<SGrade>) {
        val profile = getMeProfile()

        grades.forEach {
            val code = it.discipline.split("-")[0].trim()
            val groups = getClassGroup(code, it.semesterId, profile.uid)
            if (groups.isEmpty()) Timber.d("<grades_group_404> :: Groups not found for ${code}_${it.semesterId}_${profile.name}")
            else {
                if (groups.size == 1) {
                    val group = groups[0]
                    val cs = getClassStudent(group.uid, profile.uid)
                    prepareInsertion(cs, it)
                } else {
                    val value = groups.firstOrNull { g -> !g.group.startsWith("P") }
                    if (value == null) {
                        Timber.e("<grades_no_T_found> :: This will be ignored forever ${code}_${it.semesterId}_${profile.name} ")
                    } else {
                        val cs = getClassStudent(value.uid, profile.uid)
                        prepareInsertion(cs, it)
                    }
                }
            }
        }
    }

    private fun prepareInsertion(cs: ClassStudent, it: SGrade) {
        val values = HashMap<String, SGradeInfo>()

        try {
            cs.finalScore = it.finalScore.toDouble()
            updateCS(cs)
        } catch (t: Throwable) {
            Timber.d("Final Score of discipline is not available")
        }

        it.values.forEach {g ->
            var grade = values[g.name]
            if (grade == null) { grade = g }
            else {
                if (g.hasGrade()) grade = g
                else if (g.hasDate() && grade.hasDate() && g.date != grade.date) grade = g
                else Timber.d("This grade was ignored ${g.name}_${g.grade}")
            }

            values[g.name] = grade
        }

        values.values.forEach{ i ->
            val grade = getNamedGradeDirect(cs.uid, i.name)
            if (grade == null) {
                val notified = if (i.hasGrade()) 3 else 1
                insert(Grade(classId = cs.uid, name = i.name, date = i.date, notified = notified, grade = i.grade))
            } else {
                if (grade.hasGrade() && i.hasGrade() && grade.grade != i.grade) {
                    grade.notified = 4
                    grade.grade = i.grade
                    grade.date = i.date
                } else if (!grade.hasGrade() && i.hasGrade()) {
                    grade.notified = 3
                    grade.grade = i.grade
                    grade.date = i.date
                } else if (!grade.hasGrade() && !i.hasGrade() && grade.date != i.date) {
                    grade.notified = 2
                    grade.date = i.date
                } else {
                    Timber.d("No changes detected between $grade and $i")
                }
                update(grade)
            }
        }
    }

    @Update(onConflict = REPLACE)
    protected abstract fun updateCS(cs: ClassStudent)

    @Query("SELECT * FROM Grade WHERE class_id = :classId AND name = :name")
    protected abstract fun getNamedGradeDirect(classId: Long, name: String): Grade?

    @Query("SELECT cs.* FROM ClassStudent cs WHERE cs.group_id = :groupId AND cs.profile_id = :profileId")
    protected abstract fun getClassStudent(groupId: Long, profileId: Long): ClassStudent

    @Query("SELECT g.* FROM ClassGroup g, Class c, Discipline d, Semester s, ClassStudent cs WHERE g.class_id = c.uid AND c.semester_id = s.uid AND c.discipline_id = d.uid AND s.sagres_id = :semesterId AND d.code = :code AND cs.profile_id = :profileId AND g.uid = cs.group_id")
    protected abstract fun getClassGroup(code: String, semesterId: Long, profileId: Long): List<ClassGroup>

    @Query("SELECT * FROM Profile WHERE me = 1")
    protected abstract fun getMeProfile(): Profile
}