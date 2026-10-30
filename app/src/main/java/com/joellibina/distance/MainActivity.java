package com.joellibina.distance;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends Activity {
    LinearLayout root;
    TextView preview;
    int activeTab=0;
    final java.util.Set<String> expandedNotes=new java.util.HashSet<>();
    final android.os.Handler uiHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    LinearLayout pageContainer;
    final int bg=Color.rgb(19,17,30), panel=Color.rgb(39,32,55), white=Color.WHITE;
    @Override public void onCreate(Bundle b) { super.onCreate(b); getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(bg); build(); DailyReceiver.schedule(this); CoupleWidget.refresh(this); if(GoogleLogin.signedIn()) { FirebaseBridge.restorePair(this); FirebaseBridge.listen(this); EventStore.start(this); } SyncWorker.schedule(this); ReminderWorker.schedule(this); ReminderWorker.runNow(this); if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},17); }
    LinearLayout section() { LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(24,22,24,22);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,16);l.setLayoutParams(p);l.setBackgroundColor(panel);root.addView(l);return l; }
    TextView label(LinearLayout parent,String text,int size){TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(white);t.setPadding(0,7,0,9);parent.addView(t);return t;}
    EditText edit(LinearLayout parent,String initial,String hint) {EditText e=new EditText(this);e.setSingleLine(true);e.setText(initial);e.setHint(hint);e.setTextColor(white);e.setHintTextColor(0xffaf9faf);parent.addView(e);return e;}
    Button button(LinearLayout p,String text,Runnable action) {Button b=new Button(this);b.setText(text);p.addView(b);b.setOnClickListener(v->action.run());return b;}
    CheckBox check(LinearLayout p,String title,String key,boolean def) {CheckBox c=new CheckBox(this);c.setText(title);c.setTextColor(white);c.setChecked(CoupleData.getBool(this,key,def));p.addView(c);c.setOnCheckedChangeListener((v,checked)->{CoupleData.setBool(this,key,checked);changed();});return c;}
    void changed(){CoupleWidget.refresh(this);updatePreview();}
    void updatePreview(){ if(preview!=null) preview.setText(CoupleData.get(this,"names","Joel ♥ Libina")+"\n\nOur distance: "+CoupleData.distance(this)+"\n"+CoupleData.countdown(this)+"\n"+CoupleData.together(this)+"\n"+CoupleData.weddingCountdown()+"\n\n"+CoupleData.message(this)); }
    void pickTimestamp(String key, boolean dateOnly) {
        Calendar now=Calendar.getInstance();long old=CoupleData.getLong(this,key);if(old>0)now.setTimeInMillis(old);
        DatePickerDialog d=new DatePickerDialog(this,(picker,y,m,day)->{
            Calendar chosen=Calendar.getInstance();chosen.set(y,m,day,12,0,0);chosen.set(Calendar.MILLISECOND,0);
            if(dateOnly){ CoupleData.setLong(this,key,chosen.getTimeInMillis());changed();build(); }
            else new TimePickerDialog(this,(v,h,min)->{chosen.set(Calendar.HOUR_OF_DAY,h);chosen.set(Calendar.MINUTE,min);CoupleData.setLong(this,key,chosen.getTimeInMillis());changed();build();},now.get(Calendar.HOUR_OF_DAY),now.get(Calendar.MINUTE),false).show();
        },now.get(Calendar.YEAR),now.get(Calendar.MONTH),now.get(Calendar.DAY_OF_MONTH));d.show();
    }
    void pickAnniversary(){ Calendar c=Calendar.getInstance();String existing=CoupleData.get(this,"anniversary","");try{LocalDate d=LocalDate.parse(existing);c.set(d.getYear(),d.getMonthValue()-1,d.getDayOfMonth());}catch(Exception ignored){}
        new DatePickerDialog(this,(v,y,m,day)->{CoupleData.set(this,"anniversary",LocalDate.of(y,m+1,day).toString());changed();build();},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show(); }
    void cityPicker(LinearLayout p,int n){
        String k=Integer.toString(n); label(p,n==1?"JOEL'S CITY":"LIBINA'S CITY",14);
        label(p,CoupleData.get(this,"city"+k,n==1?"Atlanta, Georgia, USA":"Frisco, Texas, USA"),15);
        button(p,"Search and select a worldwide city",()->CitySearch.open(this,n,()->{changed();build();}));
    }
    String dateString(long ms){if(ms==0)return "Not set";return Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a z"));}
    @Override protected void onResume(){super.onResume();EventStore.onChange=()->{if(activeTab==1)refreshDatesSoon();};if(GoogleLogin.signedIn()) { FirebaseBridge.restorePair(this); FirebaseBridge.listen(this); EventStore.start(this); }}
    @Override protected void onPause(){EventStore.onChange=null;FirebaseBridge.stop();super.onPause();}
    void refreshDatesSoon(){uiHandler.removeCallbacks(refreshDatesTask);uiHandler.postDelayed(refreshDatesTask,220);}
    final Runnable refreshDatesTask=()->{if(activeTab==1 && !isFinishing())build();};
    void authFeedback(String message) {
        runOnUiThread(() -> {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            if (GoogleLogin.signedIn()) { FirebaseBridge.restorePair(this); FirebaseBridge.listen(this); EventStore.start(this); }
            build();
        });
    }
    void firebaseSection(){
        LinearLayout p=section();label(p,"PRIVATE SYNC — EMAIL ACCOUNT",16);
        if(!GoogleLogin.signedIn()){
            label(p,"Sign in or create an account to sync your shared cities and dates.",13);
            EditText email=edit(p,"","Email address");
            email.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            EditText password=edit(p,"","Password (8+ characters for new accounts)");
            password.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            button(p,"Sign in",()->GoogleLogin.signIn(this,email.getText().toString(),password.getText().toString(),this::authFeedback));
            button(p,"Create account",()->GoogleLogin.createAccount(this,email.getText().toString(),password.getText().toString(),this::authFeedback));
            button(p,"Forgot password?",()->GoogleLogin.resetPassword(email.getText().toString(),this::authFeedback));
            return;
        }
        label(p,"Signed in: "+FirebaseAuth.getInstance().getCurrentUser().getEmail(),12);
        button(p,"Sign out",()->{
            FirebaseBridge.stop(); GoogleLogin.signOut(); build();
        });
        label(p,"No GPS or tracking. Either partner can edit BOTH cities and sync to both widgets.",13);
        label(p,"Pair status: "+(FirebaseBridge.isPaired(this)?"Connected":"Not paired"),13);
        String code=FirebaseBridge.invite(this);
        if(!code.isEmpty()){
            TextView key=label(p,"Private invitation: "+code,12);key.setTextIsSelectable(true);
            button(p,"Copy pairing code",()->{
                android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Invitation",code));
                Toast.makeText(this,"Copied",Toast.LENGTH_SHORT).show();
            });
        }
        if(!FirebaseBridge.isPaired(this)){
            button(p,"Create pair — first phone",()->FirebaseBridge.create(this,m->runOnUiThread(()->{
                Toast.makeText(this,m,Toast.LENGTH_LONG).show();build();
            })));
            EditText join=edit(p,"","Paste invitation code");
            button(p,"Join pair — second phone",()->FirebaseBridge.join(this,join.getText().toString(),m->runOnUiThread(()->{
                Toast.makeText(this,m,Toast.LENGTH_LONG).show();build();
            })));
        }else{
            button(p,"Save Shared Details",()->FirebaseBridge.syncShared(this,m->runOnUiThread(()->{
                Toast.makeText(this,m,Toast.LENGTH_LONG).show();changed();build();
            })));
            button(p,"Refresh from Firebase",()->{
                FirebaseBridge.fetch(this);
                Toast.makeText(this,"Fetching changes",Toast.LENGTH_SHORT).show();
            });
            long latest=CoupleData.getLong(this,"lastSync");
            label(p,"Last synchronized: "+(latest==0?"Not yet":dateString(latest)),12);
        }
        label(p,"After editing shared details, tap Save Shared Details. Android may delay background widget refreshes.",12);
    }
    void eventsSection(){
        LinearLayout p=section();
        label(p,"Your dates are shared after pairing. Reminders: 7, 5, 1, and 0 days by default.",13);
        label(p,"Cloud status: "+EventStore.state(this),13).setTextColor(0xffdac3e6);
        button(p,"Add important date",()->editEvent(null));
        button(p,"Refresh from Firebase",()->{EventStore.fetch(this);Toast.makeText(this,"Refreshing dates",Toast.LENGTH_SHORT).show();});
        java.util.List<EventStore.Event> events=EventStore.visible(this);
        events.sort((a,b)->b.date.compareTo(a.date));
        if(events.isEmpty())label(p,"No important dates yet.",14);
        DateTimeFormatter format=DateTimeFormatter.ofPattern("MMMM d, yyyy",java.util.Locale.ENGLISH);
        for(EventStore.Event e:events){
            LinearLayout card=section();
            GradientDrawable background=new GradientDrawable();background.setColor(0xff332943);background.setCornerRadius(22);card.setBackground(background);
            TextView heading=label(card,e.title,17);heading.setTypeface(null,Typeface.BOLD);
            String pretty=e.date;try{pretty=LocalDate.parse(e.date).format(format);}catch(Exception ignored){}
            label(card,pretty+(e.annual?" · Every year":""),14).setTextColor(0xffe1c5d5);
            label(card,EventStore.description(e,LocalDate.now()),13);
            if(!e.note.isEmpty()){
                String key=e.id;
                boolean expanded=expandedNotes.contains(key);
                TextView note=label(card,expanded?e.note:(e.note.length()>95?e.note.substring(0,95)+"…":e.note),14);
                if(e.note.length()>95){
                    Button expand=button(card,expanded?"Show less":"Read more",()->{if(expanded)expandedNotes.remove(key);else expandedNotes.add(key);refreshDatesSoon();});
                    expand.setTextSize(12);
                }
            }
            Button editButton=button(card,"Edit",()->editEvent(e));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.gravity=android.view.Gravity.END;editButton.setLayoutParams(lp);editButton.setTextSize(12);editButton.setAllCaps(false);
        }
    }
    void editEvent(EventStore.Event old){
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(26,10,26,10);
        label(form,"EVENT NAME",14);
        EditText title=edit(form,old==null?"":old.title,"e.g. Wedding anniversary");
        label(form,"DATE (YYYY-MM-DD)",14);
        EditText date=edit(form,old==null?"":old.date,"YYYY-MM-DD");
        label(form,"Date format: YYYY-MM-DD, e.g. 2027-01-03",12);
        CheckBox annual=new CheckBox(this);annual.setText("Repeat every year");annual.setChecked(old==null||old.annual);form.addView(annual);
        label(form,"MEMORY / NOTES",14);
        EditText note=edit(form,old==null?"":old.note,"Optional notes");note.setSingleLine(false);
        label(form,"REMIND ME (DAYS BEFORE)",14);
        EditText days=edit(form,old==null?"7,5,1,0":old.reminders,"7,5,1,0");
        annual.setTextColor(white);
        android.widget.ScrollView eventScroll=new android.widget.ScrollView(this);eventScroll.addView(form);
        new android.app.AlertDialog.Builder(this,android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle(old==null?"Add a special date":"Edit special date").setView(eventScroll)
          .setPositiveButton("Save",(dialog,which)->{
            EventStore.Event event=new EventStore.Event(old==null?"":old.id,title.getText().toString().trim(),date.getText().toString().trim(),note.getText().toString().trim(),annual.isChecked(),days.getText().toString().trim());
            EventStore.save(this,event,message->runOnUiThread(()->{Toast.makeText(this,message,Toast.LENGTH_LONG).show();refreshDatesSoon();}));
          })
          .setNeutralButton(old==null?" ":"Delete",(dialog,which)->{
            if(old==null)return;
            new android.app.AlertDialog.Builder(this).setMessage("Delete "+old.title+" on both phones?")
              .setPositiveButton("Delete",(d,w)->{
                  java.util.List<EventStore.Event> remaining=EventStore.local(this);
                  remaining.removeIf(x->x.id.equals(old.id));EventStore.persist(this,remaining);refreshDatesSoon();
                  new android.app.AlertDialog.Builder(this).setTitle("Date removed")
                    .setMessage("Undo removing “"+old.title+"”? Choose Keep deleted to confirm the cloud deletion.")
                    .setPositiveButton("Undo",(x,y)->{java.util.List<EventStore.Event> restored=EventStore.local(this);restored.removeIf(z->z.id.equals(old.id));restored.add(old);EventStore.persist(this,restored);refreshDatesSoon();})
                    .setNegativeButton("Keep deleted",(x,y)->EventStore.delete(this,old,message->runOnUiThread(this::refreshDatesSoon)))
                    .setCancelable(false).show();
              })
              .setNegativeButton("Cancel",null).show();
          }).setNegativeButton("Cancel",null).show();
    }
    void premiumSection(){
        LinearLayout p=section(); label(p,"PREMIUM EXPERIENCE & BATTERY",17);
        label(p,"Your personal theme and sync profile stay on this phone. No GPS or background tracking.",12);
        String[] themes={"Midnight Rose","Ivory Romance","Sage & Cream","OLED Black","Transparent","Glass"};
        Spinner theme=new Spinner(this);theme.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,themes));
        String saved=CoupleData.get(this,"premiumTheme","Midnight Rose");for(int i=0;i<themes.length;i++)if(themes[i].equals(saved))theme.setSelection(i);p.addView(theme);
        String[] battery={"Eco (4 hours)","Balanced (2 hours)","Fast sync (30 minutes)"};
        Spinner profile=new Spinner(this);profile.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,battery));profile.setSelection(Math.max(0,Math.min(2,(int)CoupleData.number(this,"batteryProfile",1))));p.addView(profile);
        button(p,"Save theme and battery profile",()->{CoupleData.set(this,"premiumTheme",themes[theme.getSelectedItemPosition()]);CoupleData.set(this,"batteryProfile",""+profile.getSelectedItemPosition());
           String t=themes[theme.getSelectedItemPosition()];CoupleData.set(this,"style",(t.equals("Transparent")?"Transparent":"Glass"));
           CoupleData.set(this,"textColor",t.equals("Ivory Romance")||t.equals("Sage & Cream")?"Black":"White");SyncWorker.schedule(this);changed();});
        CheckBox togetherMode=check(p,"Together Mode (show marriage duration instead of distance)","togetherMode",false);
        label(p,"Relationship date and marriage date are configured in Our Story. Together Mode can be switched on or off any time.",12);
    }
    void switchTab(int n){activeTab=n;build();}
    void homeContent(){
        label(root,"OUR LITTLE WORLD",13).setTextColor(0xffe8b7ce);
        label(root,"Joel ♥ Libina",28).setTypeface(null,Typeface.BOLD);
        LinearLayout p=section();label(p,"OUR DISTANCE & REUNION",14);
        preview=label(p,"",20);updatePreview();button(p,"Refresh widget",this::changed);
        java.util.List<EventStore.Event> events=EventStore.local(this);
        events.sort((a,b)->Long.compare(EventStore.daysUntil(a,LocalDate.now()),EventStore.daysUntil(b,LocalDate.now())));
        p=section();label(p,"COMING UP ♥",17);
        int count=0;for(EventStore.Event e:events){if(EventStore.daysUntil(e,LocalDate.now())>=0&&count++<3)label(p,e.title+" · "+EventStore.description(e,LocalDate.now()),15);}
        if(count==0)label(p,"Add your first important date from the Dates tab.",14);
        button(p,"View all important dates",()->switchTab(1));
    }
    void datesContent(){
        label(root,"Important Dates ♥",26).setTypeface(null,Typeface.BOLD);
        eventsSection();
    }
    void settingsContent(){
        label(root,"Settings",26).setTypeface(null,Typeface.BOLD);
        LinearLayout p=section();label(p,"NAMES",14);EditText names=edit(p,CoupleData.get(this,"names","Joel ♥ Libina"),"Couple names");button(p,"Save names",()->{CoupleData.set(this,"names",names.getText().toString());changed();});
        p=section();label(p,"WORLDWIDE CITIES",16);cityPicker(p,1);cityPicker(p,2);label(p,"City centers only. No GPS. Changes synchronize after you save shared settings.",12);
        p=section();label(p,"NEXT REUNION",16);label(p,dateString(CoupleData.getLong(this,"reunion")),15);button(p,"Set reunion date and time",()->pickTimestamp("reunion",false));button(p,"Clear reunion",()->{CoupleData.setLong(this,"reunion",0);changed();build();});
        p=section();label(p,"OUR STORY",16);label(p,"Together since: "+dateString(CoupleData.getLong(this,"together")),14);button(p,"Set relationship start date",()->pickTimestamp("together",true));label(p,"Anniversary: "+CoupleData.get(this,"anniversary","Not set"),14);button(p,"Set anniversary",this::pickAnniversary);check(p,"Show story duration","showTogether",true);check(p,"Show anniversary if no together date","showAnniversary",true);check(p,"Anniversary notification","reminders",false);
        p=section();label(p,"MARRIAGE",16);label(p,"Marriage date: "+dateString(CoupleData.getLong(this,"marriageDate")),14);button(p,"Set marriage date",()->pickTimestamp("marriageDate",true));
        p=section();label(p,"WIDGET CONTENT",16);label(p,"Choose what appears over your wallpaper. Wedding countdown hides automatically after Jan 3, 2027, 11 AM IST.",12);
        check(p,"Distance (miles only) / married duration in Together Mode","widgetDistance",true);
        check(p,"Reunion countdown","widgetReunion",true);
        check(p,"Our story in months and weeks","widgetStory",true);
        check(p,"Wedding countdown (auto-hide after wedding)","widgetWedding",true);
        check(p,"Next important date","widgetNext",false);
        check(p,"Show daily romantic quote","widgetRomance",false);
        p=section();label(p,"WIDGET APPEARANCE",16);String[] styles={"Transparent","Glass"};Spinner style=new Spinner(this);style.setAdapter(spinnerAdapter(styles));style.setSelection(CoupleData.get(this,"style","Transparent").equals("Glass")?1:0);p.addView(style);String[] colors={"White","Pink","Black"};Spinner color=new Spinner(this);color.setAdapter(spinnerAdapter(colors));String picked=CoupleData.get(this,"textColor","White");color.setSelection(picked.equals("Pink")?1:picked.equals("Black")?2:0);p.addView(color);button(p,"Save appearance",()->{CoupleData.set(this,"style",styles[style.getSelectedItemPosition()]);CoupleData.set(this,"textColor",colors[color.getSelectedItemPosition()]);changed();});check(p,"Rotate romantic quotes every 24 hours","messages",true);
        firebaseSection();premiumSection();
        p=section();label(p,"REMINDERS",16);
        label(p,"Notifications are scheduled for around 9:00 AM local time (Android may delay them to conserve battery).",12);
        button(p,"Test notification",()->ReminderWorker.testNotification(this));
        button(p,"Reschedule reminders",()->{ReminderWorker.schedule(this);Toast.makeText(this,"Reminders rescheduled",Toast.LENGTH_SHORT).show();});
        button(p,"Check notification permission",()->{
            if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},17);
            else Toast.makeText(this,"Notification permission enabled",Toast.LENGTH_SHORT).show();
        });
        
    }
    ArrayAdapter<String> spinnerAdapter(String[] items){ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,items);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);return a;}
    void build(){
        preview=null;
        LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);shell.setBackgroundColor(bg);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg);root.setPadding(18,28,18,12);scroll.addView(root);
        shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(activeTab==0)homeContent();else if(activeTab==1)datesContent();else settingsContent();
        LinearLayout navigation=new LinearLayout(this);navigation.setOrientation(LinearLayout.HORIZONTAL);navigation.setPadding(4,6,4,6);navigation.setBackgroundColor(0xff251e30);
        String[] titles={"⌂ Home","▦ Dates","⚙ Settings"};
        for(int i=0;i<3;i++){final int n=i;TextView tab=new TextView(this);tab.setGravity(android.view.Gravity.CENTER);tab.setText(titles[i]);tab.setTextSize(13);tab.setTextColor(activeTab==i?0xffffc4da:0xffc6b8ca);tab.setPadding(1,14,1,14);if(activeTab==i)tab.setTypeface(null,Typeface.BOLD);navigation.addView(tab,new LinearLayout.LayoutParams(0,-2,1));tab.setOnClickListener(v->switchTab(n));}
        shell.addView(navigation);
        // Android 15+ enforces edge-to-edge when targeting API 35.
        // Keep both the header and tab bar outside the status/navigation bars.
        shell.setOnApplyWindowInsetsListener((view, insets) -> {
            final int top, bottom;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(
                    android.view.WindowInsets.Type.statusBars() |
                    android.view.WindowInsets.Type.navigationBars() |
                    android.view.WindowInsets.Type.displayCutout());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(0, top, 0, bottom);
            return insets;
        });
        setContentView(shell);
        shell.requestApplyInsets();
    }
}
