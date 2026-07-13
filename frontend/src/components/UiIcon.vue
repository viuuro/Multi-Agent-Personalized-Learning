<template>
  <svg
    class="ui-icon"
    viewBox="0 0 24 24"
    fill="none"
    aria-hidden="true"
  >
    <template v-if="name === 'menu'">
      <path d="M5.5 7h13M5.5 12h13M5.5 17h13" />
    </template>
    <template v-else-if="name === 'plus'">
      <path d="M12 5.5v13M5.5 12h13" />
    </template>
    <template v-else-if="name === 'close'">
      <path d="m6.8 6.8 10.4 10.4M17.2 6.8 6.8 17.2" />
    </template>
    <template v-else-if="name === 'edit'">
      <path d="m5 19 3.7-.8L18.8 8.1a2 2 0 0 0-2.9-2.9L5.8 15.3 5 19Z" />
      <path d="m14.7 6.4 2.9 2.9" />
    </template>
    <template v-else-if="name === 'expand'">
      <path d="M9 4H4v5M15 4h5v5M9 20H4v-5M15 20h5v-5" />
    </template>
    <template v-else-if="name === 'upload'">
      <path d="M12 16V4.5M7.8 8.7 12 4.5l4.2 4.2" />
      <path d="M5 15.5v3A1.5 1.5 0 0 0 6.5 20h11a1.5 1.5 0 0 0 1.5-1.5v-3" />
    </template>
    <template v-else-if="name === 'send'">
      <path d="M12 18V6M7.8 10.2 12 6l4.2 4.2" />
    </template>
    <template v-else-if="name === 'chevron-left'">
      <path d="m15.5 5.5-6.5 6.5 6.5 6.5" />
    </template>
    <template v-else-if="name === 'chevron-right'">
      <path d="m8.5 5.5 6.5 6.5-6.5 6.5" />
    </template>
    <template v-else-if="name === 'image'">
      <rect x="3.5" y="4.5" width="17" height="15" rx="2.5" />
      <circle cx="9" cy="9.5" r="1.5" />
      <path d="m5.5 17 4.3-4.3 3.1 3.1 2.1-2.1 3.5 3.3" />
    </template>
    <template v-else-if="name === 'file'">
      <path d="M6.5 3.5h7l4 4v13h-11a2 2 0 0 1-2-2v-13a2 2 0 0 1 2-2Z" />
      <path d="M13.5 3.5v4h4M8 12h6M8 16h6" />
    </template>
    <template v-else-if="name === 'new-chat'">
      <path d="M13 18H8l-4 2v-4A3 3 0 0 1 3 13.8V7a3 3 0 0 1 3-3h8a3 3 0 0 1 3 3v3" />
      <path d="M18 13v7M14.5 16.5h7" />
    </template>
    <template v-else-if="name === 'delete'">
      <path d="M4.5 7h15M9 7V4h6v3M7 7l1 13h8l1-13M10 11v5M14 11v5" />
    </template>
    <template v-else-if="name === 'check'">
      <path d="m5.5 12.5 4 4 9-9" />
    </template>
    <template v-else-if="name === 'link'">
      <path d="m8.5 15.5-1 1a3.5 3.5 0 0 1-5-5l3-3a3.5 3.5 0 0 1 5 0M15.5 8.5l1-1a3.5 3.5 0 0 1 5 5l-3 3a3.5 3.5 0 0 1-5 0M8.5 15.5l7-7" />
    </template>
    <template v-else-if="name === 'refresh'">
      <path d="M20 7v5h-5M4 17v-5h5" />
      <path d="M6.1 8A7 7 0 0 1 18.5 7.5L20 12M4 12l1.5 4.5A7 7 0 0 0 17.9 16" />
    </template>
    <template v-else-if="name === 'user'">
      <circle cx="12" cy="7.5" r="3.5" />
      <path d="M4.5 20a7.5 7.5 0 0 1 15 0" />
    </template>
    <template v-else-if="name === 'logout'">
      <path d="M10 4H6.5A2.5 2.5 0 0 0 4 6.5v11A2.5 2.5 0 0 0 6.5 20H10" />
      <path d="m15 7 5 5-5 5M20 12H9" />
    </template>
    <template v-else-if="name === 'sun'">
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M18.7 5.3l-1.4 1.4M6.7 17.3l-1.4 1.4" />
    </template>
    <template v-else-if="name === 'moon'">
      <path d="M20 15.5A8.2 8.2 0 0 1 8.5 4 8.5 8.5 0 1 0 20 15.5Z" />
    </template>
    <template v-else-if="name === 'monitor'">
      <rect x="3" y="4" width="18" height="13" rx="2" />
      <path d="M8 21h8M12 17v4" />
    </template>
  </svg>
</template>

<script setup lang="ts">
defineProps<{
  name: 'menu' | 'plus' | 'close' | 'edit' | 'expand' | 'upload' | 'send'
    | 'chevron-left' | 'chevron-right' | 'image' | 'file' | 'new-chat'
    | 'delete' | 'check' | 'link' | 'refresh' | 'user' | 'logout'
    | 'sun' | 'moon' | 'monitor'
}>()
</script>

<style scoped>
.ui-icon {
  width: 1em;
  height: 1em;
  flex: 0 0 auto;
  overflow: visible;
  stroke: currentColor;
  stroke-width: 1.3;
  stroke-linecap: round;
  stroke-linejoin: round;
  shape-rendering: geometricPrecision;
}

.ui-icon * {
  vector-effect: non-scaling-stroke;
}
</style>
