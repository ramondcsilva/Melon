/*
 * Copyright (c) 2018.
 * João Paulo Sena <joaopaulo761@gmail.com>
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

package com.forcetower.sagres.parsers;

import com.forcetower.sagres.database.model.SCalendar;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import timber.log.Timber;

/**
 * Created by João Paulo on 06/03/2018.
 */

public class SagresCalendarParser {

    public static List<SCalendar> getCalendar(@NonNull Document document) {
        Element element = document.selectFirst("div[class=\"webpart-calendario\"]");
        if (element == null) {
            Timber.d("Calendar not found");
            return null;
        }

        if (element.childNodeSize() < 2) {
            Timber.d("Calendar found, but not able to parse");
            return null;
        }

        List<SCalendar> items = new ArrayList<>();
        Element events = element.child(1);
        Element ul = events.selectFirst("ul");

        for (Element li : ul.select("li")) {
            String text = li.text();
            int index = text.indexOf("-");
            String days = text.substring(0, index);
            String event = text.substring(index + 1);
            items.add(new SCalendar(days, event.trim()));
        }

        return items;
    }
}
