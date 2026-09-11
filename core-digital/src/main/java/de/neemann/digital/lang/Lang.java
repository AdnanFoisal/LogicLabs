/*
 * Copyright (c) 2016 Helmut Neemann
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.lang;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * Headless string localization utility.
 * Replaces desktop Preferences and GUI Bundle dependencies with pure JVM resource loading.
 */
public final class Lang {

    private static volatile Lang instance;

    public static class Language {
        private final String name;

        public Language(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static Lang getInstance() {
        if (instance == null) {
            synchronized (Lang.class) {
                if (instance == null) {
                    instance = new Lang();
                }
            }
        }
        return instance;
    }

    /**
     * Gets an internationalized string.
     *
     * @param key    the key
     * @param params optional parameters
     * @return the internationalized string or the formatted key if no translation present
     */
    public static String get(String key, Object... params) {
        return getInstance().getKey(key, params);
    }

    /**
     * Gets an internationalized string or null if not found.
     *
     * @param key    the key
     * @param params optional parameters
     * @return the internationalized string or null
     */
    public static String getNull(String key, Object... params) {
        return getInstance().getKeyNull(key, params);
    }

    /**
     * Evaluates multilingual content string.
     *
     * @param text input text
     * @return evaluated text
     */
    public static String evalMultilingualContent(String text) {
        if (text == null) return "";
        return text;
    }

    /**
     * Sets the language without using Preferences.
     *
     * @param language the language
     */
    public static void setLanguage(Language language) {
        getInstance().loadLanguage(language != null ? language.getName() : "en");
    }

    /**
     * @return current language
     */
    public static Language currentLanguage() {
        return getInstance().currentLanguage;
    }

    private final Map<String, String> strings = new HashMap<>();
    private Language currentLanguage = new Language("en");

    private Lang() {
        loadLanguage("en");
    }

    private synchronized void loadLanguage(String lang) {
        this.currentLanguage = new Language(lang);
        strings.clear();

        // 1. Try loading specified language
        loadXml("/lang/lang_" + lang + ".xml");
        // 2. Fallback to English if not fully covered
        if (!"en".equals(lang)) {
            loadXmlFallback("/lang/lang_en.xml");
        }
    }

    private void loadXmlFallback(String path) {
        try (InputStream is = Lang.class.getResourceAsStream(path)) {
            if (is == null) return;
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(is);
            NodeList list = doc.getElementsByTagName("string");
            for (int i = 0; i < list.getLength(); i++) {
                Element elem = (Element) list.item(i);
                String name = elem.getAttribute("name");
                if (!strings.containsKey(name)) {
                    strings.put(name, elem.getTextContent());
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void loadXml(String path) {
        try (InputStream is = Lang.class.getResourceAsStream(path)) {
            if (is == null) return;
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(is);
            NodeList list = doc.getElementsByTagName("string");
            for (int i = 0; i < list.getLength(); i++) {
                Element elem = (Element) list.item(i);
                String name = elem.getAttribute("name");
                strings.put(name, elem.getTextContent());
            }
        } catch (Exception ignored) {
        }
    }

    private String getKey(String key, Object... params) {
        String val = strings.get(key);
        if (val == null) {
            if (params != null && params.length > 0) {
                try {
                    return MessageFormat.format(key, params);
                } catch (Exception e) {
                    return key;
                }
            }
            return key;
        }
        if (params != null && params.length > 0) {
            try {
                return MessageFormat.format(val, params);
            } catch (Exception e) {
                return val;
            }
        }
        return val;
    }

    private String getKeyNull(String key, Object... params) {
        String val = strings.get(key);
        if (val == null) {
            return null;
        }
        if (params != null && params.length > 0) {
            try {
                return MessageFormat.format(val, params);
            } catch (Exception e) {
                return val;
            }
        }
        return val;
    }
}
