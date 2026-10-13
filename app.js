// Browser implementation of TripPlanner.java's transport model and min-max weighted scoring.
const MODES=[
{name:"Walk",co2:0,cost:0,base:0,speed:5,overhead:0,max:20},
{name:"Bike",co2:0,cost:0,base:0,speed:15,overhead:0,max:80},
{name:"Bus",co2:.089,cost:1.5,base:50,speed:45,overhead:.5,max:1500},
{name:"Train",co2:.041,cost:2,base:80,speed:80,overhead:.5,max:3000},
{name:"Car",co2:.171,cost:6,base:0,speed:60,overhead:.1,max:3000},
{name:"Flight",co2:.255,cost:5,base:1500,speed:750,overhead:3,max:20000}
];
const money=n=>n.toFixed(2);
function fmtTime(h){let mins=Math.round(h*60);return `${Math.floor(mins/60)}h ${String(mins%60).padStart(2,"0")}m`;}
function compute(distance,travellers,seats,weights){
 const opts=MODES.filter(m=>distance<=m.max).map(m=>{
 const units=m.name==="Car"?Math.ceil(travellers/seats):travellers;
 const cost=(m.base+distance*m.cost)*units;
 const emissions=distance*m.co2*units;
 return {mode:m.name,units,cost,costPP:cost/travellers,emissionsPP:emissions/travellers,hours:m.overhead+distance/m.speed};
 });
 const sum=weights.reduce((a,b)=>a+b,0);
 const w=sum===0?[1/3,1/3,1/3]:weights.map(x=>x/sum);
 const keys=["costPP","hours","emissionsPP"];
 const limits=keys.map(k=>[Math.min(...opts.map(o=>o[k])),Math.max(...opts.map(o=>o[k]))]);
 opts.forEach(o=>o.score=keys.reduce((total,k,i)=>{const [min,max]=limits[i];return total+w[i]*(max===min?0:(o[k]-min)/(max-min));},0));
 return opts.sort((a,b)=>a.score-b.score);
}
document.getElementById("trip-form").addEventListener("submit",e=>{
 e.preventDefault();
 const f=new FormData(e.currentTarget);
 const distance=Number(f.get("distance")), travellers=Number(f.get("travellers")),seats=Number(f.get("seats"));
 const weights=["wc","wt","we"].map(k=>Number(f.get(k)));
 const valid=distance>=.1&&distance<=20000&&Number.isInteger(travellers)&&travellers>=1&&travellers<=100&&Number.isInteger(seats)&&seats>=1&&seats<=7&&weights.every(x=>Number.isFinite(x)&&x>=0&&x<=1000);
 const el=document.getElementById("results");
 if(!valid){el.textContent="Please enter valid values in all fields.";return;}
 const opts=compute(distance,travellers,seats,weights),best=opts[0];
 const worst=opts.reduce((a,b)=>a.emissionsPP>b.emissionsPP?a:b);
 const saved=worst.emissionsPP-best.emissionsPP;
 el.innerHTML=`<div class="result-banner"><h3>🌿 Recommended: ${best.mode}</h3><p>${money(best.costPP)} currency units per person · ${fmtTime(best.hours)} · ${money(best.emissionsPP)} kg CO₂ per person</p>${saved>0?`<p>Potential saving: <strong>${money(saved)} kg CO₂ per person</strong> compared with ${worst.mode}.</p>`:""}</div><div class="table-scroll"><table><thead><tr><th>Mode</th><th>Units</th><th>Cost/person</th><th>Total cost</th><th>Time</th><th>CO₂/person (kg)</th><th>Score</th></tr></thead><tbody>${opts.map((o,i)=>`<tr class="${i===0?"best":""}"><td>${o.mode}</td><td>${o.units}</td><td>${money(o.costPP)}</td><td>${money(o.cost)}</td><td>${fmtTime(o.hours)}</td><td>${money(o.emissionsPP)}</td><td>${o.score.toFixed(3)}</td></tr>`).join("")}</tbody></table></div><p class="muted">Lower score is better. Units are tickets, except cars, which counts vehicles.</p>`;
});
const streakKey="terratrek-green-streak-v1";
function day(d){return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,"0")}-${String(d.getDate()).padStart(2,"0")}`;}
let streak={days:0,lastDate:null};
try{const s=JSON.parse(localStorage.getItem(streakKey));if(s&&Number.isInteger(s.days)&&s.days>=0&&typeof s.lastDate==="string")streak=s;}catch(e){}
function renderStreak(){
 const done=streak.lastDate===day(new Date());
 document.getElementById("streak-days").textContent=streak.days;
 const btn=document.getElementById("record-trip");btn.disabled=done;
 btn.textContent=done?"Today's greener trip recorded ✓":"I chose a greener trip today";
 document.getElementById("streak-msg").textContent=done?"Great choice! Come back tomorrow.":streak.days?"Your next greener trip keeps your streak alive.":"Your next greener trip starts your streak!";
}
document.getElementById("record-trip").addEventListener("click",()=>{
 const now=new Date(),today=day(now),yesterday=new Date(now);yesterday.setDate(yesterday.getDate()-1);
 if(streak.lastDate===today)return;
 streak.days=streak.lastDate===day(yesterday)?streak.days+1:1;
 streak.lastDate=today;
 try{localStorage.setItem(streakKey,JSON.stringify(streak));}catch(e){}
 renderStreak();
});
renderStreak();
const routes=[
{name:"Main Road & Market Street",time:"22 min",distance:"1.8 km",tags:["Good lighting","Shops nearby","Busy streets"],lighting:true,busy:true,transit:true,isolated:true,emissions:"Low emissions"},
{name:"Riverside Walk",time:"19 min",distance:"1.5 km",tags:["Scenic","Check lighting after dark"],lighting:false,busy:false,transit:false,isolated:false,emissions:"Walking"},
{name:"Residential Streets",time:"25 min",distance:"2.0 km",tags:["Residential area"],lighting:true,busy:false,transit:false,isolated:true,emissions:"Walking"}
];
const active=new Set(["lighting","busy"]);
function renderRoutes(){
 const ranked=routes.map((r,i)=>({...r,index:i,score:[...active].filter(k=>r[k]).length})).sort((a,b)=>b.score-a.score||a.index-b.index);
 document.getElementById("routes").innerHTML=ranked.map((r,i)=>`<article class="route"><h4>${i+1}. ${r.name} ${i===0?'<small style="color:#a5e9b4">· Preference match</small>':""}</h4><p>${r.time} · ${r.distance} · ${r.emissions}</p><div class="tags">${r.tags.map(t=>`<span>${t}</span>`).join("")}</div><button data-detail="${r.index}">View route details</button><p class="details" hidden id="detail-${r.index}">Illustrative route only. No real-world navigation or verified lighting, activity, or safety information is available.</p></article>`).join("");
}
document.querySelectorAll("[data-filter]").forEach(b=>b.addEventListener("click",()=>{
 const k=b.dataset.filter;active.has(k)?active.delete(k):active.add(k);
 b.classList.toggle("on",active.has(k));b.setAttribute("aria-pressed",String(active.has(k)));renderRoutes();
}));
document.getElementById("routes").addEventListener("click",e=>{
 const b=e.target.closest("[data-detail]");if(!b)return;
 const p=document.getElementById("detail-"+b.dataset.detail);p.hidden=!p.hidden;
 b.textContent=p.hidden?"View route details":"Hide route details";
});
renderRoutes();
document.getElementById("rate-toggle").addEventListener("click",()=>{const p=document.getElementById("rating-panel");p.hidden=!p.hidden;});
document.getElementById("save-rating").addEventListener("click",()=>{
 const id=document.getElementById("rating-route").value,value=document.getElementById("rating-value").value;
 const msg=document.getElementById("rating-feedback");
 try{localStorage.setItem("terratrek-demo-personal-rating-"+id,value);msg.textContent=`Your personal demo rating (${value}/5) was saved in this browser only. It is not a public safety report.`;}
 catch(e){msg.textContent="Browser storage is unavailable; rating was not saved.";}
});
