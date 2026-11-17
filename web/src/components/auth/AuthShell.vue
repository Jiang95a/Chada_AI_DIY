<script setup lang="ts">
defineProps<{
  title: string
  description: string
}>()
</script>

<template>
  <main class="auth-page">
    <div class="auth-lines" aria-hidden="true">
      <i class="auth-line auth-line--h" />
      <i class="auth-line auth-line--v" />
      <i class="auth-dot auth-dot--one" />
      <i class="auth-dot auth-dot--two" />
      <i class="auth-dot auth-dot--three" />
    </div>

    <section class="auth-shell">
      <div class="auth-logo">
        <span class="auth-logo-mark"><i /><b /></span>
        <strong>心屿同行</strong>
      </div>
      <h1>{{ title }}</h1>
      <p class="auth-copy">{{ description }}</p>
      <slot />
    </section>
  </main>
</template>

<style>
.auth-page {
  position: relative;
  display: grid;
  min-height: 100vh;
  overflow: hidden;
  padding: 28px;
  background:
    radial-gradient(circle at 50% 48%, rgb(72 111 94 / 5%), transparent 36%),
    var(--bg);
  place-items: center;
}

.auth-lines {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.auth-line {
  position: absolute;
  top: 50%;
  left: 50%;
  display: block;
  background: var(--brand-200);
  opacity: 0.55;
}

.auth-line--h {
  width: min(980px, 86vw);
  height: 1px;
  transform: translate(-50%, -50%);
}

.auth-line--v {
  width: 1px;
  height: min(720px, 80vh);
  transform: translate(-50%, -50%);
}

.auth-dot {
  position: absolute;
  display: block;
  width: 7px;
  height: 7px;
  border: 1px solid var(--brand-300);
  border-radius: 50%;
  background: var(--bg);
}

.auth-dot--one {
  top: 18%;
  left: 58%;
}

.auth-dot--two {
  top: 44%;
  left: 12%;
}

.auth-dot--three {
  right: 13%;
  bottom: 19%;
}

.auth-shell {
  position: relative;
  z-index: 1;
  display: flex;
  width: min(460px, 100%);
  padding: 38px 44px 30px;
  border: 1px solid var(--line);
  border-radius: 17px;
  background: var(--surface);
  box-shadow:
    0 1px 2px rgb(24 24 27 / 4%),
    0 28px 70px -38px rgb(31 49 41 / 28%);
  flex-direction: column;
}

.auth-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-bottom: 30px;
}

.auth-logo-mark {
  position: relative;
  display: grid;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--brand-600);
  place-items: center;
}

.auth-logo-mark i,
.auth-logo-mark b {
  position: absolute;
  width: 11px;
  height: 5px;
  border: 1.2px solid #fff;
  border-radius: 99px;
}

.auth-logo-mark i {
  transform: translate(-3px, -1px) rotate(-28deg);
}

.auth-logo-mark b {
  transform: translate(3px, 2px) rotate(-28deg);
}

.auth-logo strong {
  color: var(--ink-900);
  font-size: 15px;
  font-weight: 750;
  letter-spacing: -0.02em;
}

.auth-shell h1 {
  margin: 0;
  color: var(--ink-900);
  font-family: var(--font);
  font-size: 34px;
  font-weight: 750;
  letter-spacing: -0.045em;
  line-height: 1.25;
  text-align: center;
}

.auth-copy {
  margin: 10px 0 30px;
  color: var(--muted);
  font-size: 12.5px;
  line-height: 1.7;
  text-align: center;
}

.auth-form {
  display: grid;
  gap: 20px;
}

.auth-field {
  display: grid;
  gap: 5px;
}

.auth-field > span:first-child {
  color: var(--ink-600);
  font-size: 12px;
  font-weight: 600;
}

.auth-field input {
  width: 100%;
  height: 38px;
  padding: 0 0;
  border: 0;
  border-bottom: 1px solid var(--line-strong);
  border-radius: 0;
  outline: none;
  color: var(--ink-900);
  background: transparent;
  font-size: 14px;
  transition: border-color 160ms ease;
}

.auth-field input::placeholder {
  color: var(--ink-400);
}

.auth-field input:focus {
  border-color: var(--brand-500);
}

.auth-password-field {
  position: relative;
  display: block;
}

.auth-password-field input {
  padding-right: 34px;
}

.auth-password-toggle {
  position: absolute;
  top: 50%;
  right: -3px;
  display: grid;
  width: 30px;
  height: 30px;
  padding: 0;
  border: 0;
  color: var(--ink-400);
  background: transparent;
  cursor: pointer;
  transform: translateY(-50%);
  place-items: center;
}

.auth-password-toggle:hover {
  color: var(--brand-700);
}

.auth-actions {
  display: grid;
  gap: 9px;
  margin-top: 7px;
  grid-template-columns: 1.35fr 1fr;
}

.auth-submit,
.auth-secondary {
  height: 43px;
  border-radius: 5px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.auth-submit {
  border: 0;
  color: #fff;
  background: var(--brand-800);
}

.auth-submit:hover:not(:disabled) {
  background: var(--brand-700);
}

.auth-submit:disabled {
  opacity: 0.58;
  cursor: wait;
}

.auth-secondary {
  border: 1px solid var(--line);
  color: var(--brand-700);
  background: var(--surface-warm);
}

.auth-secondary:hover {
  border-color: var(--brand-300);
}

.auth-forgot {
  display: flex;
  justify-content: center;
  margin-top: 13px;
}

.auth-link {
  padding: 0;
  border: 0;
  color: var(--brand-700);
  background: transparent;
  font-size: 12px;
  cursor: pointer;
}

.auth-link:hover {
  color: var(--brand-800);
  text-decoration: underline;
}

.auth-links {
  display: flex;
  justify-content: center;
  gap: 22px;
  margin-top: 15px;
}

.auth-footer-note {
  margin: 22px 0 0;
  color: var(--ink-500);
  font-size: 11px;
  line-height: 1.6;
  text-align: center;
}

.auth-rules {
  display: grid;
  gap: 4px;
  margin: 0;
  padding: 0;
  color: var(--ink-500);
  font-size: 11px;
  list-style: none;
}

.auth-rules li {
  display: flex;
  align-items: center;
  gap: 6px;
}

.auth-rules li::before {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--ink-300);
  content: '';
}

.auth-rules li.is-pass {
  color: var(--brand-700);
}

.auth-rules li.is-pass::before {
  background: var(--brand-500);
}

.auth-strength {
  display: grid;
  gap: 5px;
  color: var(--ink-500);
  font-size: 11px;
}

.auth-strength-bars {
  display: grid;
  gap: 4px;
  grid-template-columns: repeat(3, 1fr);
}

.auth-strength-bars i {
  height: 4px;
  background: var(--ink-100);
}

.auth-strength-bars i.is-pass {
  background: var(--brand-500);
}

.auth-hint {
  color: var(--muted);
  font-size: 11px;
  line-height: 1.6;
}

.auth-error,
.auth-success {
  margin: 0;
  padding: 9px 11px;
  border: 1px solid transparent;
  border-radius: 5px;
  font-size: 11.5px;
  line-height: 1.6;
}

.auth-error {
  border-color: #e6c7c4;
  color: #9a433c;
  background: #fbefed;
}

.auth-success {
  border-color: var(--brand-200);
  color: var(--brand-800);
  background: var(--brand-50);
}

.auth-form .auth-submit {
  margin-top: 2px;
}

.auth-shell .reset-result {
  display: grid;
  gap: 12px;
}

.auth-shell .reset-result > span {
  display: grid;
  width: 38px;
  height: 38px;
  margin: 0 auto;
  border-radius: 50%;
  color: var(--brand-700);
  background: var(--brand-100);
  place-items: center;
}

.auth-shell .reset-link-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  text-decoration: none;
}

@media (max-width: 520px) {
  .auth-page {
    padding: 14px;
  }

  .auth-shell {
    padding: 28px 22px 22px;
    border-radius: 13px;
  }

  .auth-shell h1 {
    font-size: 28px;
  }

  .auth-copy {
    margin-bottom: 22px;
  }

  .auth-actions {
    grid-template-columns: 1fr;
  }
}
</style>
