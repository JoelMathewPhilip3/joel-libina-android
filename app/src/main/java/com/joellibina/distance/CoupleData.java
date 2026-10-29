package com.joellibina.distance;

import android.content.Context;
import android.content.SharedPreferences;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

final class CoupleData {
    private static final String FILE = "couple_settings";
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(FILE, Context.MODE_PRIVATE); }
    static String get(Context c, String key, String def) { return prefs(c).getString(key, def); }
    static long getLong(Context c, String key) { return prefs(c).getLong(key, 0L); }
    static boolean getBool(Context c, String key, boolean def) { return prefs(c).getBoolean(key, def); }
    static void set(Context c, String key, String value) { prefs(c).edit().putString(key, value).apply(); }
    static void setLong(Context c, String key, long v) { prefs(c).edit().putLong(key,v).apply(); }
    static void setBool(Context c, String key, boolean v) { prefs(c).edit().putBoolean(key,v).apply(); }

    static final String[] CITIES = {"Atlanta, Georgia", "Frisco, Texas", "Dallas, Texas", "Lawrenceville, Georgia", "New York, New York", "London, UK", "Kochi, India", "Pathanapuram, India", "Tokyo, Japan", "Kyoto, Japan", "Other (custom coordinates)"};
    static final double[][] GEO = {{33.7490,-84.3880},{33.1507,-96.8236},{32.7767,-96.7970},{33.9562,-83.9879},{40.7128,-74.0060},{51.5074,-0.1278},{9.9312,76.2673},{9.0910,76.8565},{35.6762,139.6503},{35.0116,135.7681}};
    static int findCity(String s) { for(int i=0;i<CITIES.length-1;i++) if(CITIES[i].equals(s)) return i; return CITIES.length-1; }
    static double number(Context c,String k,double d) { try { return Double.parseDouble(get(c,k,Double.toString(d))); } catch(Exception e) {return d;} }
    static double km(Context c) {
        double a=number(c,"lat1",33.749), b=number(c,"lon1",-84.388), d=number(c,"lat2",33.1507), e=number(c,"lon2",-96.8236);
        double r=Math.sin(Math.toRadians((d-a)/2)); double s=Math.sin(Math.toRadians((e-b)/2));
        double h=r*r+Math.cos(Math.toRadians(a))*Math.cos(Math.toRadians(d))*s*s;
        return 6371.0088*2*Math.asin(Math.min(1, Math.sqrt(h)));
    }
    static String distance(Context c) {return String.format(Locale.US,"%,.0f mi",km(c)*0.6213711922);}
    static String countdown(Context c) {
        long target=getLong(c,"reunion"); if(target==0) return "Set reunion date ♡";
        long delta=target-System.currentTimeMillis(); if(delta<=0)return "Together at last ♥";
        long minutes=(delta+59999L)/60000L;
        return String.format(Locale.US,"Reunited in %dd %dh %dm ♥",minutes/1440,(minutes%1440)/60,minutes%60);
    }
    static String together(Context c) {
        long start=getLong(c,"together"); if(start==0)return "";
        long days=Math.max(0, ChronoUnit.DAYS.between(Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).toLocalDate(),LocalDate.now()));
        LocalDate from=Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate now=LocalDate.now();if(from.isAfter(now))return "Our story begins soon ♥";
        long months=ChronoUnit.MONTHS.between(from,now);
        LocalDate afterMonths=from.plusMonths(months);
        long weeks=ChronoUnit.WEEKS.between(afterMonths,now);
        return months+" months, "+weeks+" weeks since our story began";
    }
    static final long WEDDING_UTC=ZonedDateTime.of(2027,1,3,11,0,0,0,ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();
    static String weddingCountdown() {
        long remain=WEDDING_UTC-System.currentTimeMillis();if(remain<=0)return "";
        long minutes=(remain+59999L)/60000L;
        return String.format(Locale.US,"💍 Wedding in %dd %dh %dm",minutes/1440,(minutes%1440)/60,minutes%60);
    }
    static String married(Context c) {
        long marriage=getLong(c,"marriageDate");
        if(marriage==0)return "Set marriage date in Settings";
        LocalDate from=Instant.ofEpochMilli(marriage).atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate now=LocalDate.now();if(now.isBefore(from))return "Our wedding is coming ♥";
        long years=ChronoUnit.YEARS.between(from,now); LocalDate afterYears=from.plusYears(years);
        long months=ChronoUnit.MONTHS.between(afterYears,now);
        long days=ChronoUnit.DAYS.between(afterYears.plusMonths(months),now);
        return years+" years, "+months+" months, "+days+" days married ♥";
    }
    static String anniversary(Context c) {
        String date=get(c,"anniversary",""); if(date.isEmpty())return "";
        try { LocalDate d=LocalDate.parse(date); LocalDate now=LocalDate.now(); int year=now.getYear();
          LocalDate next=d.withYear(year); if(next.isBefore(now))next=d.withYear(year+1);
          long days=ChronoUnit.DAYS.between(now,next); return days==0 ? "Happy anniversary ♥" : "Anniversary in "+days+" days";
        }catch(Exception ex){return "";}
    }
    // Fixed date-based rotation: one quote per local calendar day, without repeats in 50 days.
    private static final String[] ROMANTIC_QUOTES = {
        "Every mile is worth it ♥",
        "Different cities, same heartbeat ♥",
        "One day closer to forever ♥",
        "Home is wherever you are ♥",
        "Our love has no distance ♥",
        "Every sunrise brings us closer ♥",
        "My favorite place is next to you ♥",
        "Until I can hold you again ♥",
        "Two hearts, one beautiful story ♥",
        "Forever is worth the wait ♥",
        "Every day, I choose you ♥",
        "Our hearts are always together ♥",
        "A thousand miles, one love ♥",
        "You are my favorite hello ♥",
        "You make every day brighter ♥",
        "Love travels any distance ♥",
        "Soon, no more goodbyes ♥",
        "We are worth every moment ♥",
        "You feel like home to me ♥",
        "My heart knows the way to you ♥",
        "Each day leads back to you ♥",
        "Together is my favorite place ♥",
        "Far apart, never alone ♥",
        "You are always in my heart ♥",
        "Our forever starts with us ♥",
        "Nothing changes my love for you ♥",
        "One love, two locations ♥",
        "Still falling for you daily ♥",
        "The wait makes us stronger ♥",
        "I carry you everywhere ♥",
        "Counting moments until us ♥",
        "You are my sweetest thought ♥",
        "Wherever you go, my love goes ♥",
        "The best days are ahead ♥",
        "Our story is my favorite ♥",
        "Every night, under one sky ♥",
        "Love makes the miles smaller ♥",
        "A little closer each morning ♥",
        "Our promise crosses oceans ♥",
        "You are worth the journey ♥",
        "Distance cannot divide hearts ♥",
        "Thinking of you, always ♥",
        "I am yours, near or far ♥",
        "Our next hug will be magic ♥",
        "Every moment is for us ♥",
        "The same moon watches over us ♥",
        "Until the distance disappears ♥",
        "One heartbeat closer today ♥",
        "You are my happy place ♥",
        "Always you, always us ♥"
    };
    static String message(Context c) {
        if (!getBool(c,"messages",true)) return "";
        int index=(int)Math.floorMod(LocalDate.now().toEpochDay(),ROMANTIC_QUOTES.length);
        return ROMANTIC_QUOTES[index];
    }
}
