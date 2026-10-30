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
 private static final Set<String> uploads=Collections.synchronizedSet(new HashSet<>());
 private static final Set<String> deletions=Collections.synchronizedSet(new HashSet<>());
 private static final Set<String> edits=Collections.synchronizedSet(new HashSet<>());
 static Runnable onChange;
 static void notifyChanged(Context c){if(onChange!=null && c instanceof android.app.Activity)((android.app.Activity)c).runOnUiThread(onChange);}
 static String signature(Event e){return e.title.trim()+"\u0001"+e.date+"\u0001"+e.note.trim()+"\u0001"+e.annual+"\u0001"+e.reminders;}
 static List<Event> visible(Context c){
  LinkedHashMap<String,Event> bySignature=new LinkedHashMap<>();
  for(Event e:local(c)){
   String sig=signature(e);
   Event prior=bySignature.get(sig);
   if(prior==null || (isPending(prior)&&!isPending(e)))bySignature.put(sig,e);
  }
  return new ArrayList<>(bySignature.values());
 }
 static String state(Context c){
  if(uid()==null)return "On phone only — sign in to sync";
  String s=CoupleData.get(c,"eventSyncStatus","Not yet synchronized");
  return s;
 }
 static void mark(Context c,String message){CoupleData.set(c,"eventSyncStatus",message);notifyChanged(c);}
 static List<Event> local(Context c){List<Event> list=new ArrayList<>();try{JSONArray a=new JSONArray(CoupleData.get(c,"event_cache","[]"));for(int i=0;i<a.length();i++)list.add(Event.parse(a.getJSONObject(i)));}catch(Exception ignored){}return list;}
 static void persist(Context c,List<Event> events){JSONArray a=new JSONArray();for(Event e:events)a.put(e.json());CoupleData.set(c,"event_cache",a.toString());ReminderWorker.schedule(c);ReminderWorker.runNow(c);notifyChanged(c);}
 static CollectionReference collection(Context c){return FirebaseFirestore.getInstance().collection("rooms").document(FirebaseBridge.room(c)).collection("events");}
 static String uid(){return com.google.firebase.auth.FirebaseAuth.getInstance().getUid();}
 static CollectionReference privateCollection(Context c){
  String u=uid();if(u==null)throw new IllegalStateException("Sign in first");
  return FirebaseFirestore.getInstance().collection("users").document(u).collection("privateEvents");
 }
 static CollectionReference activeCollection(Context c){return FirebaseBridge.isPaired(c)?collection(c):privateCollection(c);}
 static boolean isPending(Event e){return e.id.startsWith("local_");}
 
 static void mergeSnapshot(Context c,QuerySnapshot snapshot){
  List<Event> values=new ArrayList<>();
  for(DocumentSnapshot d:snapshot.getDocuments()){
   String title=d.getString("title"),date=d.getString("date");if(title==null||date==null)continue;
   Boolean annual=d.getBoolean("annual");
   values.add(new Event(d.getId(),title,date,Objects.toString(d.getString("note"),""),annual!=null&&annual,Objects.toString(d.getString("reminders"),"7,5,1,0")));
  }
  Set<String> remoteIds=new HashSet<>();Set<String> remoteSignatures=new HashSet<>();
  for(Event e:values){remoteIds.add(e.id);remoteSignatures.add(signature(e));}
  for(Event pending:local(c)){
   if(deletions.contains(pending.id))continue;
   if(isPending(pending)){
    String actualId=pending.id.substring("local_".length());
    if(!remoteIds.contains(actualId)&&!remoteSignatures.contains(signature(pending)))values.add(pending);
   }else if(edits.contains(pending.id)){
    values.removeIf(e->e.id.equals(pending.id));values.add(pending);
   }
  }
  persist(c,values);
 }
 static void start(Context c){
  stop();if(uid()==null)return;
  listener=activeCollection(c).addSnapshotListener((snapshot,error)->{
   if(error!=null){mark(c,"Offline / waiting to sync");return;}
   if(snapshot==null)return;
   mergeSnapshot(c,snapshot);
   if(!snapshot.getMetadata().isFromCache()&&!snapshot.getMetadata().hasPendingWrites() && uploads.isEmpty() && edits.isEmpty() && deletions.isEmpty())mark(c,"Synced · "+java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a")));else mark(c,"Syncing or offline — changes queued");
  });
  uploadLocalDrafts(c);
 }
 static void stop(){if(listener!=null){listener.remove();listener=null;}}
 static void fetch(Context c){
  if(uid()==null)return;
  activeCollection(c).get(Source.DEFAULT).addOnSuccessListener(snapshot->{mergeSnapshot(c,snapshot);mark(c,snapshot.getMetadata().isFromCache()?"Offline · cached dates":"Synced · "+java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a")));uploadLocalDrafts(c);})
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
  boolean fresh=event.id.isEmpty();
  if(fresh)event.id="local_"+UUID.randomUUID();
  List<Event> list=local(c);list.removeIf(x->x.id.equals(event.id));list.add(event);persist(c,list);
  if(uid()==null){mark(c,"On phone only — sign in to sync");cb.done("Saved locally");return;}
  mark(c,"Syncing…");cb.done("Saved · syncing");
  if(isPending(event)){uploadLocalDrafts(c);return;}
  edits.add(event.id);
  activeCollection(c).document(event.id).set(event.map()).addOnSuccessListener(v->{
   edits.remove(event.id);mark(c,"Synced · "+java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a")));fetch(c);
  }).addOnFailureListener(e->{edits.remove(event.id);mark(c,"Offline · changes pending");});
 }
 static void delete(Context c,Event event,Callback cb){
  String key=event.id;
  deletions.add(key);
  List<Event> list=local(c);list.removeIf(x->x.id.equals(key));persist(c,list);
  cb.done("Removed from view");
  if(uid()==null||isPending(event)){deletions.remove(key);mark(c,"On phone only");return;}
  mark(c,"Deleting…");
  activeCollection(c).document(key).delete().addOnSuccessListener(v->{deletions.remove(key);mark(c,"Synced · deletion saved");fetch(c);})
   .addOnFailureListener(e->{deletions.remove(key);mark(c,"Offline · deletion awaiting sync");});
 }
 static void uploadLocalDrafts(Context c){
  if(uid()==null)return;
  for(Event e:local(c)){
   if(!isPending(e)||deletions.contains(e.id)||!uploads.add(e.id))continue;
   String stableId=e.id.substring("local_".length());
   activeCollection(c).document(stableId).set(e.map())
   .addOnSuccessListener(v->{
    uploads.remove(e.id);
    List<Event> values=local(c);
    for(Event item:values)if(item.id.equals(e.id))item.id=stableId;
    // Remove duplicate remote/local copies by stable document ID.
    LinkedHashMap<String,Event> unique=new LinkedHashMap<>();for(Event item:values)unique.put(item.id,item);
    persist(c,new ArrayList<>(unique.values()));mark(c,"Synced · "+java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a")));
   }).addOnFailureListener(ex->{uploads.remove(e.id);mark(c,"Offline · changes pending");});
  }
 }
 /** Copy personal account events to the paired collection; retain private backup. */
 static void promotePersonalEvents(Context c){
  if(uid()==null||!FirebaseBridge.isPaired(c))return;
  privateCollection(c).get().addOnSuccessListener(snap->{
   for(DocumentSnapshot d:snap.getDocuments()){
    if(d.getData()==null)continue;
    DocumentReference target=collection(c).document(d.getId());
    FirebaseFirestore.getInstance().runTransaction(transaction->{
     DocumentSnapshot existing=transaction.get(target);
     if(!existing.exists())transaction.set(target,d.getData());
     return null;
    });
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
