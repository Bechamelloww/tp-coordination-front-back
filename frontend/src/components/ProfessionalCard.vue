<script setup lang="ts">
import {computed} from 'vue';import {specialties,timeLabel,dateLabel,type Professional,type Slot} from '../types';
const props=defineProps<{professional:Professional;slots:Slot[]}>();
defineEmits<{choose:[professional:Professional,slot:Slot];profile:[professional:Professional]}>();
const days=computed(()=>{const m=new Map<string,Slot[]>();for(const s of props.slots){const day=dateLabel(s.startsAt);if(!m.has(day))m.set(day,[]);m.get(day)!.push(s);}return [...m.entries()].slice(0,3)});
const initials=computed(()=>props.professional.name.replace('Dr ','').split(' ').map(s=>s[0]).join(''));
</script>
<template>
 <!-- RGAA 9.1 : le nom du praticien est un titre (h3 sous « N praticiens à découvrir »), les jours des h4. -->
 <article class="doctor-card">
  <div class="doctor-info"><div class="identity-row"><div class="avatar" aria-hidden="true">{{initials}}</div><div><h3 class="doctor-name"><button class="name-link" @click="$emit('profile',professional)">{{professional.name}}</button></h3><p class="specialty">{{specialties[professional.specialty]}}</p></div></div>
   <p class="address">{{professional.address}}<br>{{professional.city}}</p><p class="price">Consultation au cabinet · {{professional.fee}} €</p><button class="text-button" @click="$emit('profile',professional)">Voir la fiche du praticien <span class="sr-only">{{professional.name}}</span> <span aria-hidden="true">↗</span></button></div>
  <div class="availability"><p class="availability-title">Prochaines disponibilités</p><div v-if="days.length" class="days"><div v-for="[day,slotsOfDay] in days" :key="day" class="day"><h4>{{day}}</h4><button v-for="slot in slotsOfDay.slice(0,4)" :key="slot.id" class="slot" :aria-label="`Réserver avec ${professional.name} le ${day} à ${timeLabel(slot.startsAt)}`" @click="$emit('choose',professional,slot)">{{timeLabel(slot.startsAt)}}</button></div></div><p v-else class="empty-small">Aucun créneau disponible dans les 14 prochains jours.</p></div>
 </article>
</template>
