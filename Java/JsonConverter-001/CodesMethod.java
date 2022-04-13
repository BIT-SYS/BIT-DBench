package me.rothes.jsonconverter.convertmethods;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import me.rothes.jsonconverter.EnLocale;
import me.rothes.jsonconverter.JsonConverter;
import me.rothes.jsonconverter.utils.FileUtils;
import org.apache.commons.text.StringEscapeUtils;

import java.io.File;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CodesMethod implements ConvertMethod {

    private Pattern funcPattern1 = Pattern.compile("(?:stringset|msgnext)loc\\(\"([\\s\\S]*?)\", \"([\\s\\S]+?)\"\\)");
    private Pattern funcPattern2 = Pattern.compile("(?:stringset|msgnext)subloc\\(\"([\\s\\S]*?)\", [\\s\\S]+?, \"([\\s\\S]+?)\"\\)");
    private Pattern funcPattern3 = Pattern.compile("msgsetloc\\([\\s\\S]+?, \"([\\s\\S]*?)\", \"([\\s\\S]+?)\"\\)");
    private Pattern funcPattern4 = Pattern.compile("msgsetsubloc\\([\\s\\S]+?, \"([\\s\\S]*?)\", (?:[\\s\\S]+?, )*?\"([\\s\\S]+?)\"\\)");
    private Pattern funcPatternEnLang = Pattern.compile("scr_84_get_lang_string_ch[0-9]\\(\"([\\s\\S]*?)\"\\)");
    private int i = 0;
    private File codeFile = null;

    @Override
    public HashMap<String, EnLocale> getEnLocalization() {
        System.out.println("输入 codes 文件夹路径:");
        File file = new File(JsonConverter.getScanner().nextLine());
        if (!FileUtils.isExist(file)) {
            return null;
        }
        if (!file.isDirectory()) {
            System.out.println("路径不是文件夹: " + file.getAbsolutePath());
            JsonConverter.sleep(1000L);
            return null;
        }
        System.out.println("输入英文语言 json 文件路径, 最好为同章节, 可留空:");
        JsonObject enJson = null;
        String path = JsonConverter.getScanner().nextLine();
        if (!path.isEmpty()) {
            File enLang = new File(path);
            if (!FileUtils.isExist(enLang)) {
                return null;
            }
            JsonElement jsonElement = JsonParser.parseString(FileUtils.readFile(enLang));
            enJson = jsonElement.getAsJsonObject();
        }
        HashMap<String, EnLocale> map = new HashMap<>();

        File[] listFiles = file.listFiles();
        String code;
        Matcher matcher;
        System.out.println("--------------------------------------------------------------------------------                    ");
        Thread thread = new Thread() {

            private int maxLength = 0;

            @Override
            public void run() {
                if (i != listFiles.length) {
                    int addChar = codeFile.getName().length() - maxLength;
                    StringBuilder add = new StringBuilder();
                    if (addChar > 0) {
                        maxLength = codeFile.getName().length();
                    } else {
                        for (int i = 0; i >= addChar; i--) {
                            add.append(" ");
                        }
                    }
                    System.out.print("处理中.. 文件 " + i + " / " + listFiles.length + " , 当前 " + codeFile.getName() + add + "\r");
                    try {
                        Thread.sleep(20L);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    run();
                }
            }
        };
        thread.start();
        for (; i < listFiles.length; i++) {
            codeFile = listFiles[i];
            code = FileUtils.readFile(codeFile);
            matcher = funcPattern1.matcher(code);
            match(matcher, map);
            matcher = funcPattern2.matcher(code);
            match(matcher, map);
            matcher = funcPattern3.matcher(code);
            match(matcher, map);
            matcher = funcPattern4.matcher(code);
            match(matcher, map);
            matcher = funcPatternEnLang.matcher(code);
            while (matcher.find()) {
                String key = unescape(matcher.group(1));
                EnLocale get = map.get(key);
                if (get != null && get.getType() == EnLocale.LocaleType.STRING) {
                    continue;
                }
                if (enJson != null) {
                    JsonPrimitive primitive = enJson.getAsJsonPrimitive(key);
                    if (primitive == null) {
                        System.out.println("英文语言 json 缺少此键: " + key + "     ");
                        map.put(key, new EnLocale(key, EnLocale.LocaleType.LANG_KEY));
                    }
                    map.put(key, new EnLocale(primitive.getAsString(), EnLocale.LocaleType.STRING));
                } else {
                    map.put(key, new EnLocale(key, EnLocale.LocaleType.LANG_KEY));
                }
            }
        }
        System.out.println("--------------------------------------------------------------------------------                    ");
        return map;
    }

    private void match(Matcher matcher, HashMap<String, EnLocale> map) {
        while (matcher.find()) {
            String key = unescape(matcher.group(2));
            String value = unescape(matcher.group(1));
            EnLocale get = map.get(key);
            if (get != null && get.getType() == EnLocale.LocaleType.STRING) {
                continue;
            }
            map.put(key, new EnLocale(value, EnLocale.LocaleType.STRING));
        }
    }

    private String unescape(String str) {
        return StringEscapeUtils.unescapeJson(str);
    }

}
