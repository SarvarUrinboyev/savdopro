// Support / sales contact — single source of truth, shown on the public landing
// page and the in-app "Bog'lanish" (Help) page. Update these three values to
// change every contact button across the app.
export const SUPPORT = {
  telegram: 'Hanafiy709',                  // username without the leading @
  phone: '+998931645650',
  email: 'urinboyevsarvar97@gmail.com',
};

export const telegramUrl = `https://t.me/${SUPPORT.telegram}`;
export const phoneHref = `tel:${SUPPORT.phone.replace(/[^\d+]/g, '')}`;
export const emailHref = `mailto:${SUPPORT.email}`;
