import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * TerraTrek website. Uses TripPlanner.java for the calculations.
 * Needs logo.jpeg in the same folder you run it from.
 *
 * Compile: javac TripPlanner.java TerraTrekServer.java
 * Run:     java TerraTrekServer
 * Open:    http://localhost:8080
 */
public class TerraTrekServer {

    public static void main(String[] args) throws IOException {
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Logo file
        server.createContext("/logo.jpeg", ex -> {
            Path p = Paths.get("logo.jpeg");
            if (!Files.exists(p)) {
                ex.sendResponseHeaders(404, -1);
                ex.close();
                return;
            }
            byte[] bytes = Files.readAllBytes(p);
            ex.getResponseHeaders().add("Content-Type", "image/jpeg");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        });

        // Main page
        server.createContext("/", ex -> {
            Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
            byte[] html = page(q).getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, html.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(html); }
        });

        server.start();
        System.out.println("TerraTrek running at http://localhost:" + port);
    }

    // ---------- Page building ----------
    static String page(Map<String, String> q) {
        boolean submitted = q.containsKey("distance");
        double distance = num(q, "distance", 500, 0.1, 20000);
        int travellers = (int) num(q, "travellers", 2, 1, 100);
        int seats = (int) num(q, "seats", 4, 1, 7);
        double wc = num(q, "wc", 1, 0, 1000);
        double wt = num(q, "wt", 1, 0, 1000);
        double we = num(q, "we", 1, 0, 1000);

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">")
          .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
          .append("<title>TerraTrek</title><style>").append(css()).append(safetyCss()).append("</style></head><body>");

        // ===== HEADER: logo + site name =====
        sb.append("<header class=\"top\"><div class=\"brand\">")
          .append("<img src=\"/logo.jpeg\" alt=\"TerraTrek logo\" class=\"logo\">")
          .append("<span class=\"name\">TerraTrek</span>")
          .append("</div><span class=\"tag\">Compare trips by cost, time and emissions</span></header>");

        sb.append("<main>");

        // ===== Form =====
        sb.append("<form method=\"get\" action=\"/\" class=\"card\">")
          .append("<h2>Plan your trip</h2><div class=\"grid\">")
          .append(field("Distance (km)", "distance", distance, "0.1", "20000"))
          .append(field("Number of travellers", "travellers", travellers, "1", "100"))
          .append(field("Passengers per car (incl. driver)", "seats", seats, "1", "7"))
          .append("</div><h3>What matters most? (any positive numbers)</h3><div class=\"grid\">")
          .append(field("Cost weight", "wc", wc, "0", "1000"))
          .append(field("Time weight", "wt", wt, "0", "1000"))
          .append(field("Emissions weight", "we", we, "0", "1000"))
          .append("</div><button type=\"submit\">Compare options</button></form>");

        // ===== Results =====
        if (submitted) {
            List<TripPlanner.TripOption> options = TripPlanner.plan(distance, travellers, seats);
            if (options.isEmpty()) {
                sb.append("<div class=\"card\">No transport mode supports that distance.</div>");
            } else {
                double sum = wc + wt + we;
                if (sum == 0) { wc = wt = we = 1; sum = 3; }
                TripPlanner.score(options, wc / sum, wt / sum, we / sum);
                options.sort(Comparator.comparingDouble(o -> o.score));
                TripPlanner.TripOption best = options.get(0);
                TripPlanner.TripOption worst = Collections.max(options,
                        Comparator.comparingDouble(o -> o.emissionsPerPerson));
                double saved = worst.emissionsPerPerson - best.emissionsPerPerson;

                sb.append("<div class=\"card\"><h2>Results</h2>")
                  .append("<p class=\"rec\">Recommended: <b>").append(best.mode.label).append("</b> &mdash; ")
                  .append(String.format("%.2f", best.costPerPerson)).append(" per person, ")
                  .append(TripPlanner.fmtTime(best.hours)).append(", ")
                  .append(String.format("%.2f", best.emissionsPerPerson)).append(" kg CO2 per person");
                if (saved > 0) {
                    sb.append(". Saves ").append(String.format("%.2f", saved))
                      .append(" kg CO2 per person compared with ").append(worst.mode.label);
                }
                sb.append(".</p><div class=\"scroll\"><table><tr><th>Mode</th><th>Units</th>")
                  .append("<th>Cost / person</th><th>Total cost</th><th>Time</th>")
                  .append("<th>CO2 / person (kg)</th><th>Score</th></tr>");
                for (TripPlanner.TripOption o : options) {
                    sb.append(o == best ? "<tr class=\"best\">" : "<tr>")
                      .append("<td>").append(o.mode.label).append("</td>")
                      .append("<td>").append(o.units).append("</td>")
                      .append("<td>").append(String.format("%.2f", o.costPerPerson)).append("</td>")
                      .append("<td>").append(String.format("%.2f", o.cost)).append("</td>")
                      .append("<td>").append(TripPlanner.fmtTime(o.hours)).append("</td>")
                      .append("<td>").append(String.format("%.2f", o.emissionsPerPerson)).append("</td>")
                      .append("<td>").append(String.format("%.3f", o.score)).append("</td></tr>");
                }
                sb.append("</table></div><p class=\"note\">Lower score is better. Units = tickets, or cars for the car option.</p></div>");
            }
        }

        sb.append("<section class=\"card streak-card\" aria-labelledby=\"streak-title\">"
                + "<div class=\"streak-heading\"><span class=\"streak-icon\">🌱</span><div>"
                + "<h2 id=\"streak-title\">Your Green Streak</h2>"
                + "<p class=\"note\">Build a habit of choosing lower-emission travel.</p></div></div>"
                + "<div class=\"streak-count\"><span id=\"streak-days\">0</span>-day Green Streak 🌿</div>"
                + "<p id=\"streak-message\" aria-live=\"polite\">Your next greener trip starts your streak!</p>"
                + "<button type=\"button\" id=\"record-green-trip\">I chose a greener trip today</button>"
                + "<p class=\"note\">Record one lower-emission trip per day. Progress is saved in this browser. "
                + "Unavoidable travel choices never automatically reset your streak.</p></section>");
        sb.append(safetySection());
        sb.append("</main><footer>TerraTrek &copy; 2026</footer>")
          .append(streakScript()).append(safetyScript()).append("</body></html>");
        return sb.toString();
    }

    static String field(String label, String name, double value, String min, String max) {
        String v = (value == Math.floor(value)) ? String.valueOf((long) value) : String.valueOf(value);
        return "<label>" + label + "<input type=\"number\" step=\"any\" name=\"" + name + "\" value=\"" + v
                + "\" min=\"" + min + "\" max=\"" + max + "\" required></label>";
    }

    static String streakScript() {
        return "<script>" +
            "(function(){" +
            "const key='terratrek-green-streak-v1';" +
            "const count=document.getElementById('streak-days');" +
            "const msg=document.getElementById('streak-message');" +
            "const btn=document.getElementById('record-green-trip');" +
            "function day(d){return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');}" +
            "function previousDay(){let d=new Date();d.setDate(d.getDate()-1);return day(d);}" +
            "let state={days:0,lastDate:null};" +
            "try{const saved=JSON.parse(localStorage.getItem(key));if(saved&&Number.isInteger(saved.days)&&saved.days>=0&&typeof saved.lastDate==='string')state=saved;}catch(e){}" +
            "function render(){const today=day(new Date());count.textContent=state.days;" +
            "const done=state.lastDate===today;btn.disabled=done;" +
            "btn.textContent=done?'Greener trip recorded today ✓':'I chose a greener trip today';" +
            "msg.textContent=done?'Great choice! Come back after your next greener trip.':state.days?'Your next greener trip keeps your streak alive.':'Your next greener trip starts your streak!';}" +
            "btn.addEventListener('click',function(){const today=day(new Date());if(state.lastDate===today)return;" +
            "state.days=state.lastDate===previousDay()?state.days+1:1;state.lastDate=today;" +
            "try{localStorage.setItem(key,JSON.stringify(state));}catch(e){}render();});" +
            "render();" +
            "})();" +
            "</script>";
    }

    static String css() {
        return "*{box-sizing:border-box}"
             + "body{margin:0;font-family:Segoe UI,Arial,sans-serif;background:#f4f8f2;color:#23301f}"
             + ".top{background:#fff;border-bottom:3px solid #84a768;padding:10px 24px;display:flex;"
             + "align-items:center;justify-content:space-between;flex-wrap:wrap;gap:8px}"
             + ".brand{display:flex;align-items:center;gap:12px}"
             + ".logo{height:64px;width:auto;display:block}"
             + ".name{font-size:2rem;font-weight:700;color:#5f8447;letter-spacing:.5px}"
             + ".tag{color:#6b7d62;font-size:.95rem}"
             + "main{max-width:900px;margin:24px auto;padding:0 16px}"
             + ".card{background:#fff;border-radius:12px;padding:20px;margin-bottom:20px;"
             + "box-shadow:0 2px 8px rgba(0,0,0,.07)}"
             + "h2{margin-top:0;color:#5f8447}h3{margin:18px 0 8px;font-size:1rem}"
             + ".grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:14px}"
             + "label{display:flex;flex-direction:column;font-size:.9rem;gap:4px}"
             + "input{padding:8px;border:1px solid #c5d4bb;border-radius:6px;font-size:1rem}"
             + "button{margin-top:18px;background:#84a768;color:#fff;border:0;border-radius:8px;"
             + "padding:10px 22px;font-size:1rem;cursor:pointer}button:hover{background:#6f9356}"
             + ".scroll{overflow-x:auto}table{width:100%;border-collapse:collapse}"
             + "th,td{padding:8px 10px;text-align:left;border-bottom:1px solid #e3ebdd;white-space:nowrap}"
             + "th{background:#eef4ea}tr.best td{background:#e4f1da;font-weight:600}"
             + ".rec{background:#eef4ea;padding:10px 14px;border-radius:8px}"
             + ".note{font-size:.85rem;color:#6b7d62}footer{text-align:center;color:#6b7d62;padding:20px}"
             + ".streak-card{border:1px solid #d7e8ce;background:linear-gradient(135deg,#fff,#f1f9ed)}"
             + ".streak-heading{display:flex;align-items:center;gap:12px}.streak-heading h2{margin-bottom:4px}"
             + ".streak-heading .note{margin:0}.streak-icon{font-size:2.5rem}"
             + ".streak-count{font-size:clamp(1.5rem,4vw,2.2rem);font-weight:800;color:#4e7937;margin:20px 0 8px}"
             + "#record-green-trip:disabled{background:#b7c9ab;cursor:default}";
    }

    // Safety feature: illustrative UI only; no mapping or verified safety data.
    static String safetySection() {
        return """
            <section class="safety" id="women-safety" aria-labelledby="safety-title">
              <div class="safety-top"><span>♧ TerraTrek · Women's Safety</span><span>♢</span></div>
              <div class="safety-hero"><h2 id="safety-title">Your route. Your comfort.</h2>
              <p>Explore the women's safety route finder prototype.</p></div>
              <div class="safety-content">
                <h3>Find your route</h3>
                <div class="route-inputs"><label>FROM<input id="route-from" type="text" placeholder="Enter starting point" autocomplete="off"></label>
                <label>TO<input id="route-to" type="text" placeholder="Enter destination" autocomplete="off"></label></div>
                <h3>What matters to you?</h3>
                <div class="safety-filters" role="group" aria-label="Route preferences">
                  <button type="button" class="selected" data-filter="lighting" aria-pressed="true">Well-lit roads</button>
                  <button type="button" class="selected" data-filter="busy" aria-pressed="true">Busy streets</button>
                  <button type="button" data-filter="transit" aria-pressed="false">Public transport</button>
                  <button type="button" data-filter="isolated" aria-pressed="false">Avoid isolated areas</button>
                </div>
                <h3>♧ Example recommended routes</h3>
                <p class="safety-disclaimer">Illustrative routes only. These are not mapped to your entered locations and have no verified safety ratings.</p>
                <div id="safety-routes" aria-live="polite"></div>
                <p class="safety-note">♢ <b>A note on safety</b><br>Community ratings reflect personal experiences and cannot guarantee safety. Conditions may change depending on the time of day. This demo does not provide live routing or emergency assistance.</p>
                <button type="button" class="rate-route" id="rate-route">☆ Rate a demo route</button>
                <div class="rating-form" id="rating-form" hidden><label>Demo route
                  <select id="rating-route"><option value="0">Main Road &amp; Market Street</option><option value="1">Riverside Walk</option><option value="2">Residential Streets</option></select></label>
                  <label>Your rating (1–5)<select id="rating-stars"><option>5</option><option>4</option><option>3</option><option>2</option><option>1</option></select></label>
                  <button type="button" id="save-rating">Save personal demo rating</button><p id="rating-feedback" aria-live="polite"></p>
                </div>
              </div>
            </section>
            """;
    }

    static String safetyCss() {
        return """
            .safety{background:#070a09;color:#f0f4f0;border-radius:15px;overflow:hidden;margin:24px 0;box-shadow:0 4px 18px #0002}
            .safety-top{background:#173e33;padding:15px 20px;display:flex;justify-content:space-between;font-weight:600}
            .safety-hero{background:#173e33;padding:6px 20px 20px}.safety-hero h2{color:#fff;margin:0 0 7px;font-size:1.6rem}
            .safety-hero p{margin:0;color:#d5e7df}.safety-content{padding:20px}
            .safety h3{font-size:1rem;color:#f3f3f3;margin:18px 0 12px}
            .route-inputs{border:1px solid #39443f;border-radius:9px;padding:8px 12px}
            .route-inputs label{font-size:.75rem;color:#b9c5bf;padding:8px 0}
            .route-inputs label+label{border-top:1px solid #2b342f}
            .route-inputs input{background:transparent;color:white;border:0;padding:8px 0;font-size:1rem;outline-offset:2px}
            .route-inputs input::placeholder{color:#aeb8b2}
            .safety-filters{display:flex;gap:8px;flex-wrap:wrap}
            .safety-filters button{margin:0;background:#333;color:#eee;padding:8px 12px;font-size:.85rem;border:1px solid #555}
            .safety-filters button.selected{background:#15462c;color:#9df1ac;border-color:#33734b}
            .safety-disclaimer{color:#c4cbc6;font-size:.82rem}
            .route-card{background:#2b2d2c;border-radius:9px;padding:16px;margin:10px 0}
            .route-card h4{margin:0 0 8px}.route-card p{margin:7px 0;color:#d9dedb;font-size:.9rem}
            .route-tags{display:flex;gap:7px;flex-wrap:wrap;margin:12px 0}
            .route-tags span{background:#474b48;padding:5px 9px;border-radius:15px;font-size:.76rem}
            .route-card button,.rate-route{display:block;width:100%;border-radius:22px;background:#f2f2f2;color:#222;margin:12px 0 0}
            .safety-note{font-size:.84rem;line-height:1.5;color:#d1d8d3;margin:24px 0}
            .rate-route{background:transparent;color:white;border:1px solid #626e66}
            .rating-form{margin-top:12px;padding:16px;background:#202622;border-radius:10px}
            .rating-form[hidden]{display:none}.rating-form label{margin:10px 0}
            .rating-form select{padding:9px;background:#101612;color:white;border:1px solid #5a685e;border-radius:6px}
            .rating-form button{margin-top:12px}.rating-form p{color:#a5edb4}
            @media(max-width:540px){.safety-content{padding:14px}.safety-filters button{font-size:.77rem}}
            """;
    }

    static String safetyScript() {
        return """
            <script>
            (() => {
              const routes = [
                {name:'Main Road & Market Street',time:'22 min',distance:'1.8 km',tags:['Good lighting','Shops nearby','Busy streets'],lighting:true,busy:true,transit:true,isolated:true,emissions:'Low emissions'},
                {name:'Riverside Walk',time:'19 min',distance:'1.5 km',tags:['Scenic','Check lighting after dark'],lighting:false,busy:false,transit:false,isolated:false,emissions:'Walking'},
                {name:'Residential Streets',time:'25 min',distance:'2.0 km',tags:['Residential area'],lighting:true,busy:false,transit:false,isolated:true,emissions:'Walking'}
              ];
              const active = new Set(['lighting','busy']);
              const container = document.getElementById('safety-routes');
              const escapeHtml = s => String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
              function render() {
                const ranked = routes.map((r,i) => ({...r,index:i,score:[...active].filter(k=>r[k]).length})).sort((a,b)=>b.score-a.score);
                container.innerHTML = ranked.map((r,i) => `<article class="route-card"><h4>${i+1}. ${escapeHtml(r.name)} ${i===0?'<small style="color:#a4dfad">· Preference match</small>':''}</h4><p>${escapeHtml(r.time)} · ${escapeHtml(r.distance)} · ${escapeHtml(r.emissions)}</p><div class="route-tags">${r.tags.map(t=>`<span>${escapeHtml(t)}</span>`).join('')}</div><button type="button" data-details="${r.index}">View route details</button><p id="detail-${r.index}" hidden>Sample route only. Lighting and street activity are illustrative and have not been verified. Do not use this card for real-world navigation.</p></article>`).join('');
              }
              document.querySelectorAll('[data-filter]').forEach(btn=>btn.addEventListener('click',()=>{
                const key=btn.dataset.filter;active.has(key)?active.delete(key):active.add(key);
                btn.classList.toggle('selected',active.has(key));btn.setAttribute('aria-pressed',String(active.has(key)));render();
              }));
              container.addEventListener('click',e=>{
                const btn=e.target.closest('[data-details]');if(!btn)return;
                const p=document.getElementById('detail-'+btn.dataset.details);p.hidden=!p.hidden;
                btn.textContent=p.hidden?'View route details':'Hide route details';
              });
              const form=document.getElementById('rating-form');
              document.getElementById('rate-route').addEventListener('click',()=>{form.hidden=!form.hidden;});
              document.getElementById('save-rating').addEventListener('click',()=>{
                const id=document.getElementById('rating-route').value;
                const stars=document.getElementById('rating-stars').value;
                try {localStorage.setItem('terratrek-demo-personal-rating-'+id,stars);}
                catch(e) {document.getElementById('rating-feedback').textContent='Storage unavailable; rating not saved.';return;}
                document.getElementById('rating-feedback').textContent='Your personal demo rating ('+stars+'/5) was saved in this browser only. It is not a public safety report.';
              });
              render();
            })();
            </script>
            """;
    }

    // ---------- Helpers ----------
    static Map<String, String> parseQuery(String raw) {
        Map<String, String> m = new HashMap<>();
        if (raw == null || raw.isEmpty()) return m;
        for (String pair : raw.split("&")) {
            String[] kv = pair.split("=", 2);
            String k = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String v = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            m.put(k, v);
        }
        return m;
    }

    static double num(Map<String, String> q, String key, double def, double lo, double hi) {
        try {
            double v = Double.parseDouble(q.getOrDefault(key, "").trim());
            if (Double.isNaN(v) || Double.isInfinite(v)) return def;
            return Math.max(lo, Math.min(hi, v));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
