package com.joellibina.distance;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.widget.RemoteViews;

public class CoupleWidget extends AppWidgetProvider {
    static void refresh(Context context) {
        AppWidgetManager manager=AppWidgetManager.getInstance(context);
        int[] ids=manager.getAppWidgetIds(new ComponentName(context,CoupleWidget.class));
        for(int id:ids) update(context,manager,id);
    }
    static void update(Context c,AppWidgetManager manager,int id) {
        RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget);
        v.setTextViewText(R.id.widget_names,CoupleData.get(c,"names","Joel ♥ Libina"));
        String distanceLabel="Our distance: "+String.format(java.util.Locale.US,"%,.0f mi",CoupleData.km(c)*0.6213711922);
        boolean togetherMode=CoupleData.getBool(c,"togetherMode",false);
        show(v,R.id.widget_distance,CoupleData.getBool(c,"widgetDistance",true),togetherMode?CoupleData.married(c):distanceLabel);
        show(v,R.id.widget_countdown,CoupleData.getBool(c,"widgetReunion",true) && !togetherMode,CoupleData.countdown(c));
        String story=togetherMode?CoupleData.married(c):CoupleData.together(c);
        if(story.isEmpty() && CoupleData.getBool(c,"showAnniversary",true))story=CoupleData.anniversary(c);
        show(v,R.id.widget_message,CoupleData.getBool(c,"widgetStory",true),story);
        show(v,R.id.widget_wedding,CoupleData.getBool(c,"widgetWedding",true) && !CoupleData.weddingCountdown().isEmpty(),CoupleData.weddingCountdown());
        show(v,R.id.widget_next,CoupleData.getBool(c,"widgetNext",false),nextEvent(c));
        show(v,R.id.widget_romance,CoupleData.getBool(c,"widgetRomance",false),CoupleData.message(c));
        boolean glass=CoupleData.get(c,"style","Transparent").equals("Glass");
        v.setInt(R.id.widget_root,"setBackgroundResource",glass?R.drawable.glass_bg:R.drawable.transparent_bg);
        String color=CoupleData.get(c,"textColor","White");
        int textColor=color.equals("Pink")?Color.rgb(255,174,205):color.equals("Black")?Color.BLACK:Color.WHITE;
        for(int viewId:new int[]{R.id.widget_names,R.id.widget_distance,R.id.widget_countdown,R.id.widget_message,R.id.widget_wedding,R.id.widget_next,R.id.widget_romance})v.setTextColor(viewId,textColor);
        Intent intent=new Intent(c,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(c,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.widget_root,pi);
        manager.updateAppWidget(id,v);
    }
    private static void show(RemoteViews v,int id,boolean visible,String value){
        v.setViewVisibility(id,visible && value!=null && !value.isEmpty()?android.view.View.VISIBLE:android.view.View.GONE);
        v.setTextViewText(id,value==null?"":value);
    }
    private static String nextEvent(Context c){
        java.time.LocalDate today=java.time.LocalDate.now();
        EventStore.Event best=null;long bestDays=Long.MAX_VALUE;
        for(EventStore.Event e:EventStore.local(c)){
            long days=EventStore.daysUntil(e,today);
            if(days>=0&&days<bestDays){bestDays=days;best=e;}
        }
        return best==null?"":(bestDays==0?"♥ Today: ":"♥ In "+bestDays+" days: ")+best.title;
    }
    @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){ for(int id:ids)update(c,m,id); }
    @Override public void onEnabled(Context c){ DailyReceiver.schedule(c); }
}
