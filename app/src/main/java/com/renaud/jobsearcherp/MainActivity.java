package com.renaud.jobsearcherp;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.InputType;
import android.util.Base64;
import android.view.*;
import android.widget.*;

import org.json.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LinearLayout jobsContainer;
    private Spinner dateFilter;
    private EditText salaryMin, salaryMax;
    private CheckBox salaryOnly;
    private Button favoritesButton, refreshButton, syncButton, settingsButton;
    private boolean favoritesMode = false;
    private SharedPreferences prefs;
    private TextView statusText;

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("jse", MODE_PRIVATE);
        buildUi();
        if (!isConfigured()) showSettingsDialog(true); else loadJobs();
    }

    private void buildUi() {
        ScrollView outer = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        root.setBackgroundColor(Color.rgb(248,250,252));
        outer.addView(root);

        LinearLayout top = new LinearLayout(this); top.setOrientation(LinearLayout.HORIZONTAL); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this); title.setText("JobSearch ERP"); title.setTextSize(24); title.setTypeface(null, Typeface.BOLD); title.setTextColor(Color.rgb(15,23,42));
        top.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        settingsButton = button("⚙", false); top.addView(settingsButton, new LinearLayout.LayoutParams(dp(52), dp(44)));
        root.addView(top);

        statusText = new TextView(this); statusText.setText("Prêt"); statusText.setTextColor(Color.rgb(100,116,139)); statusText.setPadding(0,0,0,dp(10)); root.addView(statusText);

        LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        refreshButton = button("Actualiser", true); syncButton = button("↻ Sync", true); favoritesButton = button("★ Favoris", false);
        actions.addView(refreshButton,new LinearLayout.LayoutParams(0,dp(46),1));
        addGap(actions,8);
        actions.addView(syncButton,new LinearLayout.LayoutParams(0,dp(46),1));
        addGap(actions,8);
        actions.addView(favoritesButton,new LinearLayout.LayoutParams(0,dp(46),1));
        root.addView(actions);

        TextView filtTitle = label("Filtres"); filtTitle.setPadding(0,dp(18),0,dp(6)); root.addView(filtTitle);
        dateFilter = new Spinner(this);
        String[] dateOptions = {"Toutes les dates","Aujourd'hui","3 derniers jours","10 derniers jours","30 derniers jours"};
        dateFilter.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, dateOptions));
        root.addView(dateFilter, new LinearLayout.LayoutParams(-1,dp(48)));

        LinearLayout salaryRow = new LinearLayout(this); salaryRow.setOrientation(LinearLayout.HORIZONTAL); salaryRow.setPadding(0,dp(8),0,0);
        salaryMin = numberInput("Salaire min €"); salaryMax = numberInput("Salaire max €");
        salaryRow.addView(salaryMin,new LinearLayout.LayoutParams(0,dp(48),1)); addGap(salaryRow,8); salaryRow.addView(salaryMax,new LinearLayout.LayoutParams(0,dp(48),1)); root.addView(salaryRow);
        salaryOnly = new CheckBox(this); salaryOnly.setText("Salaire précisé uniquement"); salaryOnly.setTextColor(Color.rgb(51,65,85)); root.addView(salaryOnly);

        Button apply = button("Appliquer les filtres", true); root.addView(apply,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView sortInfo = new TextView(this); sortInfo.setText("Tri : meilleur match, puis annonces les plus récentes"); sortInfo.setTextColor(Color.rgb(100,116,139)); sortInfo.setTextSize(12); sortInfo.setPadding(0,dp(8),0,dp(10)); root.addView(sortInfo);

        jobsContainer = new LinearLayout(this); jobsContainer.setOrientation(LinearLayout.VERTICAL); root.addView(jobsContainer);
        setContentView(outer);

        refreshButton.setOnClickListener(v -> loadJobs());
        apply.setOnClickListener(v -> loadJobs());
        syncButton.setOnClickListener(v -> syncAll());
        favoritesButton.setOnClickListener(v -> { favoritesMode=!favoritesMode; favoritesButton.setText(favoritesMode?"← À traiter":"★ Favoris"); loadJobs(); });
        settingsButton.setOnClickListener(v -> showSettingsDialog(false));
    }

    private Button button(String text, boolean primary) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setTextSize(13);
        if (primary) { b.setTextColor(Color.WHITE); b.setBackgroundColor(Color.rgb(79,70,229)); }
        else { b.setTextColor(Color.rgb(15,23,42)); }
        return b;
    }
    private TextView label(String s) { TextView t=new TextView(this); t.setText(s); t.setTextSize(16); t.setTypeface(null,Typeface.BOLD); t.setTextColor(Color.rgb(15,23,42)); return t; }
    private EditText numberInput(String hint) { EditText e=new EditText(this); e.setHint(hint); e.setInputType(InputType.TYPE_CLASS_NUMBER); e.setSingleLine(true); e.setPadding(dp(12),0,dp(12),0); return e; }
    private void addGap(LinearLayout l,int w){ Space s=new Space(this); l.addView(s,new LinearLayout.LayoutParams(dp(w),1)); }

    private boolean isConfigured(){ return !prefs.getString("url","").isEmpty() && !prefs.getString("user","").isEmpty() && !prefs.getString("pass","").isEmpty(); }

    private void showSettingsDialog(boolean mandatory) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20),dp(8),dp(20),0);
        EditText url = new EditText(this); url.setHint("https://monsite.fr"); url.setText(prefs.getString("url","")); url.setInputType(InputType.TYPE_TEXT_VARIATION_URI); box.addView(url);
        EditText user = new EditText(this); user.setHint("Identifiant WordPress"); user.setText(prefs.getString("user","")); box.addView(user);
        EditText pass = new EditText(this); pass.setHint("Mot de passe d'application WordPress"); pass.setText(prefs.getString("pass","")); pass.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD); box.addView(pass);
        TextView help = new TextView(this); help.setText("WordPress → Utilisateurs → Profil → Mots de passe d’application. Crée par exemple ‘JobSearch Android’. Ton mot de passe WordPress normal n’est pas nécessaire."); help.setTextSize(12); help.setTextColor(Color.DKGRAY); help.setPadding(0,dp(8),0,0); box.addView(help);
        AlertDialog d = new AlertDialog.Builder(this).setTitle("Connexion au JobSearch ERP").setView(box).setPositiveButton("Enregistrer",null).setNegativeButton(mandatory?"Quitter":"Annuler",null).create();
        d.setOnShowListener(x -> {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String u=url.getText().toString().trim(); while(u.endsWith("/"))u=u.substring(0,u.length()-1);
                if(!u.startsWith("https://")||user.getText().toString().trim().isEmpty()||pass.getText().toString().trim().isEmpty()){ Toast.makeText(this,"Renseigne l’URL HTTPS, l’identifiant et le mot de passe d’application.",Toast.LENGTH_LONG).show(); return; }
                prefs.edit().putString("url",u).putString("user",user.getText().toString().trim()).putString("pass",pass.getText().toString().trim()).apply(); d.dismiss(); testConnection();
            });
            if(mandatory) d.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> finish());
        });
        d.setCancelable(!mandatory); d.show();
    }

    private String authHeader(){ String raw=prefs.getString("user","")+":"+prefs.getString("pass",""); return "Basic "+Base64.encodeToString(raw.getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP); }
    private String api(String path){ return prefs.getString("url","")+"/wp-json/jse/v1"+path; }

    private JSONObject request(String method,String url) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection(); c.setRequestMethod(method); c.setConnectTimeout(15000); c.setReadTimeout(120000); c.setRequestProperty("Authorization",authHeader()); c.setRequestProperty("Accept","application/json"); c.setRequestProperty("Content-Type","application/json"); if("POST".equals(method)){c.setDoOutput(true); c.getOutputStream().write("{}".getBytes(StandardCharsets.UTF_8));}
        int code=c.getResponseCode(); InputStream in=(code>=200&&code<300)?c.getInputStream():c.getErrorStream(); String body=readAll(in); if(code<200||code>=300) throw new IOException("HTTP "+code+" - "+body); return new JSONObject(body);
    }
    private String readAll(InputStream in) throws Exception { if(in==null)return ""; BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8)); StringBuilder b=new StringBuilder(); String line; while((line=r.readLine())!=null)b.append(line); return b.toString(); }

    private void testConnection(){ setBusy("Test de connexion…"); executor.execute(() -> { try{JSONObject o=request("GET",api("/health")); runOnUiThread(()->{statusText.setText("Connecté • plugin v"+o.optString("version")); setEnabled(true); loadJobs();});}catch(Exception e){showError("Connexion impossible",e);} }); }

    private int selectedDays(){ int p=dateFilter.getSelectedItemPosition(); return p==1?1:p==2?3:p==3?10:p==4?30:0; }
    private void loadJobs(){ if(!isConfigured()){showSettingsDialog(true);return;} setBusy("Chargement des annonces…"); StringBuilder q=new StringBuilder("/jobs?favorites=").append(favoritesMode?"1":"0").append("&days=").append(selectedDays()); if(!salaryMin.getText().toString().isEmpty())q.append("&salary_min=").append(salaryMin.getText()); if(!salaryMax.getText().toString().isEmpty())q.append("&salary_max=").append(salaryMax.getText()); if(salaryOnly.isChecked())q.append("&salary_specified=1"); executor.execute(()->{try{JSONObject o=request("GET",api(q.toString())); JSONArray a=o.getJSONArray("jobs"); runOnUiThread(()->renderJobs(a,o.optInt("count")));}catch(Exception e){showError("Chargement impossible",e);} }); }

    private void renderJobs(JSONArray jobs,int count){ jobsContainer.removeAllViews(); statusText.setText(count+" annonce"+(count>1?"s":"")+(favoritesMode?" en favoris":" à traiter")); if(count==0){TextView e=new TextView(this); e.setText("Aucune annonce avec ces filtres."); e.setPadding(0,dp(24),0,dp(24)); jobsContainer.addView(e); setEnabled(true);return;} for(int i=0;i<jobs.length();i++){try{jobsContainer.addView(jobCard(jobs.getJSONObject(i)));}catch(Exception ignored){}} setEnabled(true); }

    private View jobCard(JSONObject j) throws Exception {
        LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(14),dp(14),dp(14),dp(14)); card.setBackgroundColor(Color.WHITE); LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2); cp.setMargins(0,0,0,dp(12)); card.setLayoutParams(cp);
        LinearLayout head=new LinearLayout(this); head.setOrientation(LinearLayout.HORIZONTAL); head.setGravity(Gravity.TOP);
        TextView t=label(j.optString("title","Annonce")); t.setTextSize(17); head.addView(t,new LinearLayout.LayoutParams(0,-2,1));
        TextView score=new TextView(this); score.setText(j.optInt("score",0)>0?j.optInt("score")+"/100":"—"); score.setTypeface(null,Typeface.BOLD); score.setTextColor(Color.rgb(79,70,229)); score.setPadding(dp(8),0,0,0); head.addView(score); card.addView(head);
        TextView meta=new TextView(this); String company=j.optString("company","Entreprise non précisée"); String loc=j.optString("location",""); meta.setText(company+(loc.isEmpty()?"":" • "+loc)); meta.setTextColor(Color.rgb(71,85,105)); meta.setPadding(0,dp(4),0,0); card.addView(meta);
        TextView tags=new TextView(this); String salary=j.optString("salary",""); String pub=j.optString("published_label",""); String source=j.optString("source",""); tags.setText(pub+(salary.isEmpty()?"":" • "+salary)+(source.isEmpty()?"":" • "+source)); tags.setTextColor(Color.rgb(30,64,175)); tags.setPadding(0,dp(8),0,dp(8)); card.addView(tags);
        String desc=j.optString("description",""); if(desc.length()>260)desc=desc.substring(0,260)+"…"; if(!desc.isEmpty()){TextView d=new TextView(this); d.setText(desc); d.setTextColor(Color.rgb(51,65,85)); d.setTextSize(13); d.setPadding(0,0,0,dp(10)); card.addView(d);}
        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button fav=button(j.optBoolean("favorite")?"★ Retirer":"☆ Favori",false); Button open=button("Ouvrir",true); Button reject=button("Pas intéressé",false);
        actions.addView(fav,new LinearLayout.LayoutParams(0,dp(44),1)); addGap(actions,6); actions.addView(open,new LinearLayout.LayoutParams(0,dp(44),1)); addGap(actions,6); actions.addView(reject,new LinearLayout.LayoutParams(0,dp(44),1)); card.addView(actions);
        int id=j.getInt("id"); String url=j.optString("url","");
        fav.setOnClickListener(v->postAction("/job/"+id+"/favorite","Favori mis à jour"));
        open.setOnClickListener(v->{if(!url.isEmpty())startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));});
        reject.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Pas intéressé ?").setMessage("L’annonce sera supprimée du CRM mais mémorisée pour ne pas être réimportée.").setPositiveButton("Supprimer",(d,w)->postAction("/job/"+id+"/reject","Annonce écartée")).setNegativeButton("Annuler",null).show());
        return card;
    }

    private void postAction(String path,String ok){ setBusy("Mise à jour…"); executor.execute(()->{try{request("POST",api(path)); runOnUiThread(()->{Toast.makeText(this,ok,Toast.LENGTH_SHORT).show();loadJobs();});}catch(Exception e){showError("Action impossible",e);} }); }
    private void syncAll(){ setBusy("Synchronisation France Travail + Hellowork + Indeed…"); executor.execute(()->{try{JSONObject o=request("POST",api("/sync")); runOnUiThread(()->{Toast.makeText(this,"Sync terminée : FT "+o.optInt("france_travail")+", HW "+o.optInt("hellowork")+", Indeed "+o.optInt("indeed"),Toast.LENGTH_LONG).show();loadJobs();});}catch(Exception e){showError("Synchronisation impossible",e);} }); }

    private void setBusy(String s){ runOnUiThread(()->{statusText.setText(s);setEnabled(false);}); }
    private void setEnabled(boolean e){ refreshButton.setEnabled(e); syncButton.setEnabled(e); favoritesButton.setEnabled(e); settingsButton.setEnabled(e); }
    private void showError(String title,Exception e){ runOnUiThread(()->{setEnabled(true); statusText.setText("Erreur"); new AlertDialog.Builder(this).setTitle(title).setMessage(e.getMessage()+"\n\nVérifie que le plugin JobSearch ERP 3.7 est installé et que le mot de passe d’application WordPress est correct.").setPositiveButton("OK",null).show();}); }
}
