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

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy.IGNORE
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.forcetower.sagres.database.model.SDisciplineGroup
import com.forcetower.uefs.core.model.unes.Class
import com.forcetower.uefs.core.model.unes.ClassGroup
import com.forcetower.uefs.core.model.unes.ClassItem
import com.forcetower.uefs.core.model.unes.ClassMaterial
import com.forcetower.uefs.core.model.unes.Discipline
import com.forcetower.uefs.core.storage.database.accessors.GroupWithClass
import timber.log.Timber

@Dao
abstract class ClassGroupDao {
    @Insert(onConflict = IGNORE)
    abstract fun insert(group: ClassGroup): Long

    @Transaction
    open fun defineGroups(groups: List<SDisciplineGroup>) {
        for (group in groups) {
            val inserted = insert(group)
            Timber.d("Group id: ${inserted.uid}")
            for (classItem in group.classItems) {
                val item = ClassItem.createFromSagres(inserted.uid, classItem)
                insert(item)
                for (classMaterial in classItem.materials) {
                    val material = ClassMaterial.createFromSagres(inserted.uid, null, classMaterial)
                    insert(material)
                }
            }
        }
    }

    @Transaction
    open fun insert(grp: SDisciplineGroup): ClassGroup {
        val sgr = if (grp.group == null) "unique" else grp.group
        val discipline = selectDisciplineDirect(grp.code)
        var group = selectGroupDirect(grp.semester, grp.code, sgr)
        val grops = selectGroupsDirect(grp.semester, grp.code)

        if (grops.isNotEmpty() && grops[0].group.equals("unique", ignoreCase = true)) {
            group = grops[0]
        } else if (grp.group == null && grops.isNotEmpty()) {
            group = grops[0]
        } else if (group == null) {
            val clazz = selectClassDirect(grp.semester, grp.code)
            group = ClassGroup(classId = clazz.uid, group = sgr)
            group.uid = insert(group)
        }

        group.selectiveCopy(grp)
        update(group)

        if (!grp.department.isNullOrBlank()) {
            discipline.department = grp.department
            updateDiscipline(discipline)
            Timber.d("Updated discipline ${discipline.code} department to ${discipline.department}")
        }
        return group
    }

    @Update
    abstract fun update(group: ClassGroup)

    @Transaction
    @Query("SELECT * FROM ClassGroup WHERE uid = :classGroupId")
    abstract fun getWithRelations(classGroupId: Long): LiveData<GroupWithClass?>

    @Transaction
    @Query("SELECT * FROM ClassGroup WHERE uid = :classGroupId")
    abstract fun getWithRelationsDirect(classGroupId: Long): GroupWithClass?

    @Insert(onConflict = IGNORE)
    protected abstract fun insert(item: ClassItem)

    @Insert(onConflict = IGNORE)
    protected abstract fun insert(material: ClassMaterial)

    @Query("SELECT g.* FROM ClassGroup g, Class c, Semester s, Discipline d WHERE g.class_id = c.uid AND c.discipline_id = d.uid AND c.semester_id = s.uid AND s.codename = :semester AND d.code = :code AND g.`group` = :group")
    protected abstract fun selectGroupDirect(semester: String, code: String, group: String): ClassGroup?

    @Query("SELECT g.* FROM ClassGroup g, Class c, Semester s, Discipline d WHERE g.class_id = c.uid AND c.discipline_id = d.uid AND c.semester_id = s.uid AND s.codename = :semester AND d.code = :code")
    protected abstract fun selectGroupsDirect(semester: String, code: String): List<ClassGroup>

    @Query("SELECT g.* FROM ClassGroup g, Class c, Semester s, Discipline d WHERE g.class_id = c.uid AND c.discipline_id = d.uid AND c.semester_id = s.uid AND s.codename = :semester AND d.code = :code AND g.`group` = :group")
    abstract fun selectGroup(semester: String, code: String, group: String): LiveData<ClassGroup?>

    @Query("SELECT c.* FROM Class c, Semester s, Discipline d WHERE " +
            "c.discipline_id = d.uid AND " +
            "c.semester_id = s.uid AND " +
            "s.codename = :semester AND " +
            "d.code = :code")
    protected abstract fun selectClassDirect(semester: String, code: String): Class

    @Query("SELECT * FROM Discipline WHERE code = :code")
    protected abstract fun selectDisciplineDirect(code: String): Discipline

    @Update
    protected abstract fun updateDiscipline(discipline: Discipline)
}