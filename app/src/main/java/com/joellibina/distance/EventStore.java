package com.joellibina.distance;

import android.content.Context;
import com.google.firebase.firestore.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Shared event library; Firestore is authoritative, saved snapshot supports offline reminders. */
final class EventStore {
 static final class Event {
  String id, title, date, note, reminders;
  boolean annual;
  Event(String id,String title,String date,String note,boolean annual,String reminders){
   this.id=id;this.title=title;this.date=date;this.note=note;this.annual=annual;this.reminders=reminders;
  }
  Map<String,Object> map(){Map<String,Object> d=new HashMap<>();d.put("title",title);d.put("date",date);d.put("note",note);d.put("annual",annual);d.put("reminders",reminders);return d;}
  JSONObject json(){JSONObject d=new JSONObject();try{d.put("id",id);d.put("title",title);d.put("date",date);d.put("note",note);d.put("annual",annual);d.put("reminders",reminders);}catch(Exception ignored){}return d;}
  static Event parse(JSONObject d){return new Event(d.optString("id"),d.optString("title"),d.optString("date"),d.optString("note"),d.optBoolean("annual"),d.optString("reminders","7,5,1,0"));}
 }
 interface Callback {void done(String status);}
 private static ListenerRegistration listener;
 static List<Event> local(Context c){List<Event> list=new ArrayList<>();try{JSONArray a=new JSONArray(CoupleData.get(c,"event_cache","[]"));for(int i=0;i<a.length();i++)list.add(Event.parse(a.getJSONObject(i)));}catch(Exception ignored){}return list;}
 static void persist(Context c,List<Event> events){JSONArray a=new JSONArray();for(Event e:events)a.put(e.json());CoupleData.set(c,"event_cache",a.toString());ReminderWorker.schedule(c);ReminderWorker.runNow(c);}
 static CollectionReference collection(Context c){return FirebaseFirestore.getInstance().collection("rooms").document(FirebaseBridge.room(c)).collection("events");}
 static String uid(){return com.google.firebase.auth.FirebaseAuth.getInstance().getUid();}
 static CollectionReference privateCollection(Context c){
  String u=uid();if(u==null)throw new IllegalStateException("Sign in first");
  return FirebaseFirestore.getInstance().collection("users").document(u).collection("privateEvents");
 }
 static CollectionReference activeCollection(Context c){return FirebaseBridge.isPaired(c)?collection(c):privateCollection(c);}
 static boolean isPending(Event e){return e.id.startsWith("local_");}
 static void mark(Context c,String message){CoupleData.set(c,"eventSyncStatus",message);}
 static void mergeSnapshot(Context c,QuerySnapshot snapshot){
  List<Event> values=new ArrayList<>();
  for(DocumentSnapshot d:snapshot.getDocuments()){
   String title=d.getString("title"),date=d.getString("date");if(title==null||date==null)continue;
   Boolean annual=d.getBoolean("annual");
   values.add(new Event(d.getId(),title,date,Objects.toString(d.getString("note"),""),annual!=null&&annual,Objects.toString(d.getString("reminders"),"7,5,1,0")));
  }
  for(Event pending:local(c))if(isPending(pending))values.add(pending);
  persist(c,values);
 }
 static void start(Context c){
  stop();if(uid()==null)return;
  listener=activeCollection(c).addSnapshotListener((snapshot,error)->{
   if(error!=null){mark(c,"Offline / waiting to sync");return;}
   if(snapshot==null)return;
   mergeSnapshot(c,snapshot);
   mark(c,snapshot.getMetadata().hasPendingWrites()?"Syncing…":snapshot.getMetadata().isFromCache()?"Saved locally / offline":"Backed up to Firebase");
  });
  uploadLocalDrafts(c);
 }
 static void stop(){if(listener!=null){listener.remove();listener=null;}}
 static void fetch(Context c){
  if(uid()==null)return;
  activeCollection(c).get(Source.DEFAULT).addOnSuccessListener(snapshot->{mergeSnapshot(c,snapshot);mark(c,snapshot.getMetadata().isFromCache()?"Saved locally / offline":"Backed up to Firebase");uploadLocalDrafts(c);})
  .addOnFailureListener(e->mark(c,"Offline / waiting to sync"));
 }
 static boolean valid(Event event,Callback cb){
  try{LocalDate.parse(event.date);}catch(Exception e){cb.done("Enter a valid YYYY-MM-DD date.");return false;}
  if(event.title.trim().isEmpty()||event.title.length()>120||event.note.length()>1000){cb.done("Title must be 1–120 characters and notes under 1,000.");return false;}
  if(!event.reminders.matches("[0-9, ]{1,60}")){cb.done("Reminders should be comma-separated day numbers.");return false;}
  for(String s:event.reminders.split(",")){try{int n=Integer.parseInt(s.trim());if(n<0||n>365){cb.done("Reminder days must be between 0 and 365.");return false;}}catch(Exception ex){cb.done("Invalid reminder days.");return false;}}
  return true;
 }
 static void save(Context c,Event event,Callback cb){
  if(!valid(event,cb))return;
  if(event.id.isEmpty())event.id="local_"+UUID.randomUUID();
  List<Event> list=local(c);list.removeIf(x->x.id.equals(event.id));list.add(event);persist(c,list);
  if(uid()==null){mark(c,"Saved on phone · sign in to back up");cb.done("Saved on phone. Sign in to back up.");return;}
  mark(c,"Syncing…");cb.done("Saved locally · syncing to Firebase");
  uploadLocalDrafts(c);
  if(!isPending(event)){
   // Keep the same stable document ID across private and shared stores.
   activeCollection(c).document(event.id).set(event.map()).addOnSuccessListener(v->{mark(c,"Backed up to Firebase");fetch(c);})
    .addOnFailureListener(e->mark(c,"Saved locally / offline · awaiting sync"));
  }
 }
 static void delete(Context c,Event event,Callback cb){
  List<Event> list=local(c);list.removeIf(x->x.id.equals(event.id));persist(c,list);
  if(uid()==null||isPending(event)){cb.done("Removed locally.");return;}
  activeCollection(c).document(event.id).delete().addOnSuccessListener(v->{mark(c,"Backed up to Firebase");cb.done("Deleted.");})
   .addOnFailureListener(e->{mark(c,"Offline / deletion not synced");cb.done("Removed locally, cloud deletion pending. Reconnect before refreshing.");});
 }
 static void uploadLocalDrafts(Context c){
  if(uid()==null)return;
  for(Event e:local(c)){
   if(!isPending(e))continue;
   String stableId=e.id.substring("local_".length());
   activeCollection(c).document(stableId).set(e.map())
   .addOnSuccessListener(v->{
    List<Event> values=local(c);
    for(Event item:values)if(item.id.equals(e.id))item.id=stableId;
    persist(c,values);mark(c,"Backed up to Firebase");
   }).addOnFailureListener(ex->mark(c,"Saved locally / offline · awaiting sync"));
  }
 }
 /** Copy personal account events to the paired collection; retain private backup. */
 static void promotePersonalEvents(Context c){
  if(uid()==null||!FirebaseBridge.isPaired(c))return;
  privateCollection(c).get().addOnSuccessListener(snap->{
   for(DocumentSnapshot d:snap.getDocuments()){
    if(d.getData()==null)continue;
    collection(c).document(d.getId()).set(d.getData());
   }
   uploadLocalDrafts(c);
   start(c);
  }).addOnFailureListener(e->{mark(c,"Pair created · cloud migration pending");});
 }
 static LocalDate next(Event e,LocalDate today){try{LocalDate original=LocalDate.parse(e.date);if(!e.annual)return original;
  int year=today.getYear();LocalDate next=anniversary(original,year);if(next.isBefore(today))next=anniversary(original,year+1);return next;
 }catch(Exception ex){return today.minusDays(10000);}}
 static LocalDate anniversary(LocalDate original,int year){int day=Math.min(original.getDayOfMonth(),YearMonth.of(year,original.getMonth()).lengthOfMonth());return LocalDate.of(year,original.getMonth(),day);}
 static long daysUntil(Event e,LocalDate today){return ChronoUnit.DAYS.between(today,next(e,today));}
 static int years(Event e,LocalDate occurrence){try{return Math.max(0,occurrence.getYear()-LocalDate.parse(e.date).getYear());}catch(Exception ex){return 0;}}
 static String description(Event e,LocalDate today){long n=daysUntil(e,today);String when=n<0?"Past":n==0?"Today":n==1?"Tomorrow":"In "+n+" days";
  int years=e.annual?years(e,next(e,today)):0;return when+(years>0?" · "+ordinal(years)+" anniversary":"");}
 static String ordinal(int n){int m=n%100;if(m>=11&&m<=13)return n+"th";switch(n%10){case 1:return n+"st";case 2:return n+"nd";case 3:return n+"rd";default:return n+"th";}}
}
