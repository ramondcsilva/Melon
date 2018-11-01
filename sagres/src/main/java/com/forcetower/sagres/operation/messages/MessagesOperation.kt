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

package com.forcetower.sagres.operation.messages

import com.forcetower.sagres.SagresNavigator
import com.forcetower.sagres.database.model.SClass
import com.forcetower.sagres.database.model.SDisciplineResumed
import com.forcetower.sagres.database.model.SLinker
import com.forcetower.sagres.database.model.SMessage
import com.forcetower.sagres.database.model.SMessageScope
import com.forcetower.sagres.database.model.SPerson
import com.forcetower.sagres.operation.Dumb
import com.forcetower.sagres.operation.Operation
import com.forcetower.sagres.operation.Status
import com.forcetower.sagres.request.SagresCalls
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import timber.log.Timber
import java.io.IOException
import java.util.ArrayList
import java.util.concurrent.Executor

class MessagesOperation(executor: Executor?, private val userId: Long) : Operation<MessagesCallback>(executor) {
    init {
        this.perform()
    }

    override fun execute() {
        result.postValue(MessagesCallback(Status.STARTED))
        val call = SagresCalls.getMessages(userId)
        try {
            val response = call.execute()
            if (response.isSuccessful) {
                val body = response.body()!!.string()
                successMeasures(body)
            } else {
                publishProgress(MessagesCallback(Status.RESPONSE_FAILED).code(response.code()).message(response.message()))
            }
        } catch (e: IOException) {
            e.printStackTrace()
            publishProgress(MessagesCallback(Status.NETWORK_ERROR).throwable(e))
        }
    }

    private fun successMeasures(body: String) {
        try {
            val type = object: TypeToken<Dumb<ArrayList<SMessage>>>() {}.type
            val dMessages = Gson().fromJson<Dumb<ArrayList<SMessage>>>(body, type)
            val items = dMessages.items
            items.sort()
            for (message in items) {
                val person = getPerson(message.sender)
                if (person != null)
                    message.senderName = person.name
                else {
                    if (message.senderProfile == 3) {
                        message.senderName = ".UEFS."
                    }
                    Timber.d("SPerson is Invalid")
                }

                //Message is from a teacher
                if (message.senderProfile == 2) {
                    val scope = getScope(message.scopes)
                    if (scope != null) {
                        val clazz = getClazz(scope)
                        if (clazz != null) {
                            val discipline = getDiscipline(clazz)
                            if (discipline != null) {
                                Timber.d("Setting up the discipline name: ${discipline.name}")
                                message.discipline = discipline.name
                            }
                        }
                    }
                }
            }

            publishProgress(MessagesCallback(Status.SUCCESS).messages(items))
        } catch (t: Throwable) {
            publishProgress(MessagesCallback(Status.UNKNOWN_FAILURE).throwable(t).message(body))
        }
    }

    private fun getPerson(linker: SLinker): SPerson? {
        val link = linker.link
        val id = link.split("=".toRegex()).last()
        val person = SagresNavigator.instance.database.personDao().getPersonDirect(id)

        if (person != null) {
            Timber.d("Returned from cache")
            return person
        }

        val call = SagresCalls.getLink(linker)

        try {
            val response = call.execute()
            if (response.isSuccessful) {
                val body = response.body()!!.string()
                val value = gson.fromJson(body, SPerson::class.java)
                SagresNavigator.instance.database.personDao().insert(value)
                return value
            }
        } catch (e: Exception) {
            Timber.e(e)
        }

        return null
    }

    private fun getScope(scopes: SLinker): SMessageScope? {
        val link = scopes.link
        val scope = SagresNavigator.instance.database.messageScopeDao().getMessageScopeDirect(link)

        if (scope != null) {
            Timber.d("Returned scope from cache")
            return scope
        }

        val call = SagresCalls.getLink(scopes)

        try {
            val response = call.execute()
            if (response.isSuccessful) {
                val body = response.body()!!.string()
                val scoped = gson.fromJson(body, SMessageScope::class.java)
                val linker = scoped.clazzLinker
                if (linker != null) {
                    scoped.clazzLink = linker.link
                    scoped.sagresId = link
                    SagresNavigator.instance.database.messageScopeDao().insert(scoped)
                } else {
                    Timber.d("Scope linker was null")
                }
                return scoped
            }
        } catch (e: Exception) {
            Timber.e(e)
        }

        return null
    }

    private fun getClazz(scope: SMessageScope): SClass? {
        val link = scope.clazzLink?: return null

        val clazz = SagresNavigator.instance.database.clazzDao().getClazzDirect(link)
        if (clazz != null) {
            Timber.d("Returning clazz from cache")
            return clazz
        }

        val call = SagresCalls.getLink(link)
        try {
            val response = call.execute()
            if (response.isSuccessful) {
                val body = response.body()!!.string()
                val clazzed = gson.fromJson(body, SClass::class.java)
                if (clazzed.discipline != null) {
                    clazzed.disciplineLink = clazzed.discipline.link
                    clazzed.link = link
                    SagresNavigator.instance.database.clazzDao().insert(clazzed)
                } else {
                    Timber.d("Clazz linker was null")
                }
                return clazzed
            }
        } catch (e: Exception) {
            Timber.e(e)
        }

        return null
    }

    private fun getDiscipline(clazz: SClass): SDisciplineResumed? {
        val link = clazz.disciplineLink?: return null

        val discipline = SagresNavigator.instance.database.disciplineDao().getDisciplineDirect(link)
        if (discipline != null) {
            Timber.d("Returned discipline from cache")
            return discipline
        }

        val call = SagresCalls.getLink(link)
        try {
            val response = call.execute()
            if (response.isSuccessful) {
                val body = response.body()!!.string()
                val disciplined = gson.fromJson(body, SDisciplineResumed::class.java)

                val department = disciplined.department
                if (department != null) {
                    disciplined.departmentLink = department.link
                    SagresNavigator.instance.database.disciplineDao().insert(disciplined)
                }
                return disciplined
            }
        } catch (e: Exception) {
            Timber.e(e)
        }

        return null
    }
}
