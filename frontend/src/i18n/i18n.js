// Internationalisation. The Uzbek (Latin) string is used directly as the
// translation key:
//   - 'uz'  -> the key itself
//   - 'uzc' -> the key transliterated to the Cyrillic script
//   - 'ru'  -> looked up in the Russian dictionary below
// Any key missing from the Russian dictionary falls back to the Uzbek text.

export const LANGUAGES = [
  { code: 'uz', label: "O'zbekcha", short: 'UZ' },
  { code: 'uzc', label: 'Ўзбекча', short: 'ЎЗ' },
  { code: 'ru', label: 'Русский', short: 'RU' },
];

// ---------------------------------------------------- Latin -> Cyrillic

const CYR_MAP = [
  ["o'", 'ў'], ["O'", 'Ў'], ["g'", 'ғ'], ["G'", 'Ғ'],
  ['oʻ', 'ў'], ['Oʻ', 'Ў'], ['gʻ', 'ғ'], ['Gʻ', 'Ғ'],
  ['o’', 'ў'], ['O’', 'Ў'], ['g’', 'ғ'], ['G’', 'Ғ'],
  ['ya', 'я'], ['Ya', 'Я'], ['YA', 'Я'],
  ['yo', 'ё'], ['Yo', 'Ё'], ['YO', 'Ё'],
  ['yu', 'ю'], ['Yu', 'Ю'], ['YU', 'Ю'],
  ['ye', 'е'], ['Ye', 'Е'], ['YE', 'Е'],
  ['sh', 'ш'], ['Sh', 'Ш'], ['SH', 'Ш'],
  ['ch', 'ч'], ['Ch', 'Ч'], ['CH', 'Ч'],
  ['ts', 'ц'], ['Ts', 'Ц'],
  ['a', 'а'], ['b', 'б'], ['c', 'с'], ['d', 'д'], ['e', 'е'], ['f', 'ф'],
  ['g', 'г'], ['h', 'ҳ'], ['i', 'и'], ['j', 'ж'], ['k', 'к'], ['l', 'л'],
  ['m', 'м'], ['n', 'н'], ['o', 'о'], ['p', 'п'], ['q', 'қ'], ['r', 'р'],
  ['s', 'с'], ['t', 'т'], ['u', 'у'], ['v', 'в'], ['w', 'в'], ['x', 'х'],
  ['y', 'й'], ['z', 'з'],
  ['A', 'А'], ['B', 'Б'], ['C', 'С'], ['D', 'Д'], ['E', 'Е'], ['F', 'Ф'],
  ['G', 'Г'], ['H', 'Ҳ'], ['I', 'И'], ['J', 'Ж'], ['K', 'К'], ['L', 'Л'],
  ['M', 'М'], ['N', 'Н'], ['O', 'О'], ['P', 'П'], ['Q', 'Қ'], ['R', 'Р'],
  ['S', 'С'], ['T', 'Т'], ['U', 'У'], ['V', 'В'], ['W', 'В'], ['X', 'Х'],
  ['Y', 'Й'], ['Z', 'З'],
  ["'", 'ъ'], ['’', 'ъ'], ['ʼ', 'ъ'],
];

// Latin acronyms that must stay as-is even in the Cyrillic script.
const KEEP_LATIN = new Set(['USD', 'UZS', 'IMEI', 'CSV', 'XLSX', 'SKU', 'PDF', 'UZ', 'RU']);

function isUpperLatin(ch) {
  return ch >= 'A' && ch <= 'Z';
}

/** Transliterates Uzbek Latin text to the Uzbek Cyrillic script. */
export function toCyrillic(text) {
  let out = '';
  let i = 0;
  while (i < text.length) {
    if (isUpperLatin(text[i]) && isUpperLatin(text[i + 1] || '')) {
      let j = i;
      while (j < text.length && isUpperLatin(text[j])) {
        j += 1;
      }
      const run = text.slice(i, j);
      if (KEEP_LATIN.has(run)) {
        out += run;
        i = j;
        continue;
      }
    }
    let matched = false;
    for (const [lat, cyr] of CYR_MAP) {
      if (text.startsWith(lat, i)) {
        out += cyr;
        i += lat.length;
        matched = true;
        break;
      }
    }
    if (!matched) {
      out += text[i];
      i += 1;
    }
  }
  return out;
}

// ------------------------------------------------------- Russian dictionary

const RU = {
  // AI CFO assistant
  'AI CFO': 'AI финдиректор',
  'Salom! Men AI CFO yordamchingizman. "Bugun foyda nega kamaydi?" yoki "Kim qarzni kechiktiryapti?" deb so\'rang.':
      'Здравствуйте! Я ваш AI финдиректор. Спросите «Почему сегодня упала прибыль?» или «Кто задерживает оплату долга?».',
  'Bugun foyda nega kamaydi?': 'Почему сегодня упала прибыль?',
  'Qaysi tovar narxini oshiray?': 'Какому товару поднять цену?',
  'Kim qarzni kechiktiryapti?': 'Кто задерживает оплату долга?',
  'Buyurtma yaratildi': 'Заказ создан',
  'Eslatma yuborildi': 'Напоминание отправлено',
  'Buyurtma yaratish': 'Создать заказ',
  'Eslatma yuborish': 'Отправить напоминание',
  // reconciliation (bank/payments moslashtirish)
  'Moslashtirish': 'Сверка',
  'Click/Payme, bank va obuna tushumlarini ichki yozuvlar bilan solishtirish':
      'Сверка поступлений Click/Payme, банка и подписки с внутренними записями',
  'Onlayn to‘lovlar (Click/Payme) — qarz': 'Онлайн-платежи (Click/Payme) — долг',
  'Onlayn to‘lovlar': 'Онлайн-платежи',
  'Moslangan': 'Сопоставлено',
  'Kutilmoqda': 'Ожидает',
  'Yozilmagan': 'Не зачислено',
  'Bekor qilingan': 'Отменено',
  'Moslangan summa': 'Сопоставленная сумма',
  'Bu davrda onlayn to‘lov yo‘q': 'Нет онлайн-платежей за период',
  'Provayder': 'Провайдер',
  'Qarzga yozish': 'Зачислить в долг',
  'Qarzga yozildi': 'Зачислено в долг',
  'O‘zgarish bo‘lmadi': 'Изменений нет',
  'Karta terminali — sotuv (so‘mda)': 'Карт-терминал — продажи (в сумах)',
  'Terminal jami': 'Итого терминал',
  'POS karta sotuv': 'POS карт. продажи',
  'Farq': 'Разница',
  'Nomuvofiq kunlar': 'Дни с расхождением',
  'Kunlik solishtirish': 'Сверка по дням',
  'Bu davrda terminal/karta sotuvi yo‘q': 'Нет терминальных/карт. продаж за период',
  'Terminal (so‘m)': 'Терминал (сум)',
  'POS karta (so‘m)': 'POS карта (сум)',
  'Mos': 'Сходится',
  'Nomuvofiq': 'Расхождение',
  'Obuna (subscription)': 'Подписка',
  'Tarif to‘lovi va amal qilish muddati': 'Оплата тарифа и срок действия',
  'Obuna ma‘lumoti mavjud emas': 'Данные подписки недоступны',
  // purchase orders (yetkazib beruvchi buyurtmasi)
  'Yetkazib beruvchi buyurtmalari': 'Заказы поставщику',
  'Buyurtma, invoice, qabul qilish va kelish narxi tarixi':
      'Заказ, накладная, приёмка и история цен закупки',
  'Tannarx baholash': 'Оценка себестоимости',
  'Tannarx baholash (FIFO / WAC)': 'Оценка себестоимости (FIFO / WAC)',
  'Yangi buyurtma': 'Новый заказ',
  'Buyurtmalar': 'Заказы',
  'Buyurtma yo‘q': 'Нет заказов',
  'Yetkazib beruvchi': 'Поставщик',
  'Invoice': 'Накладная',
  'Invoice raqami': 'Номер накладной',
  'Buyurtma': 'Заказано',
  'Kelgan': 'Получено',
  'Buyurtma berish': 'Оформить заказ',
  'Buyurtma berildi': 'Заказ оформлен',
  'Qabul qilish': 'Принять',
  'Tovarni qabul qilish': 'Приёмка товара',
  'Tovar qabul qilindi': 'Товар принят',
  'Buyurtmani bekor qilish': 'Отменить заказ',
  'Buyurtma bekor qilindi': 'Заказ отменён',
  "Buyurtmani o'chirish": 'Удалить заказ',
  "Buyurtma o'chirildi": 'Заказ удалён',
  'Buyurtmani tahrirlash': 'Редактировать заказ',
  'Qoralama': 'Черновик',
  'Buyurtma berilgan': 'Заказан',
  'Qisman kelgan': 'Частично получен',
  'To‘liq kelgan': 'Получен полностью',
  'Buyurtma sanasi': 'Дата заказа',
  'Kutilayotgan sana': 'Ожидаемая дата',
  'Kelish narxi': 'Цена закупки',
  'Qabul sanasi': 'Дата приёмки',
  'Qoldiq': 'Остаток',
  'Qabul': 'Приём',
  'Hamma tovar qabul qilingan': 'Весь товар получен',
  'Qabul qilingan tovar tannarxi o‘rtacha-tortilgan (WAC) bo‘yicha yangilanadi.':
      'Себестоимость принятого товара обновляется по средневзвешенной (WAC).',
  'WAC jami': 'Итого WAC',
  'FIFO jami': 'Итого FIFO',
  'Omborda tovar yo‘q': 'Нет товара на складе',
  'WAC qiymat': 'Стоимость WAC',
  'FIFO qiymat': 'Стоимость FIFO',
  'Narx tarixi': 'История цен',
  'Hozirgi o‘rtacha tannarx': 'Текущая средняя себестоимость',
  'Kelish tarixi yo‘q': 'Нет истории поступлений',
  // accounting (Bosh kitob / buxgalteriya)
  'Buxgalteriya': 'Бухгалтерия',
  'Foyda va zarar': 'Прибыль и убытки',
  'Balans': 'Баланс',
  'Pul oqimi': 'Движение денег',
  'Jurnal': 'Журнал',
  'Jurnal yozuvlari': 'Записи журнала',
  'Hisoblar rejasi': 'План счетов',
  'Davrlar': 'Периоды',
  'Hisobot davrlari': 'Отчётные периоды',
  'Hisoblar': 'Счета',
  'Hisob': 'Счёт',
  'Hisob qo‘shish': 'Добавить счёт',
  'Yangi hisob': 'Новый счёт',
  'Hisobni tahrirlash': 'Редактировать счёт',
  "Hisobni o'chirish": 'Удалить счёт',
  'Hisob qo‘shildi': 'Счёт добавлен',
  'Hisob yangilandi': 'Счёт обновлён',
  "Hisob o'chirildi": 'Счёт удалён',
  'Hisob topilmadi': 'Счёт не найден',
  'Kod': 'Код',
  'Turi': 'Тип',
  'Normal qoldiq': 'Нормальный остаток',
  'Debet': 'Дебет',
  'Kredit': 'Кредит',
  'Tizim': 'Система',
  'Aktiv': 'Актив',
  'Passiv': 'Пассив',
  'Kapital': 'Капитал',
  'Daromad': 'Доход',
  'Xarajat': 'Расход',
  'Faol': 'Активен',
  'Tizim hisobining kodi va turi o‘zgartirilmaydi': 'Код и тип системного счёта изменить нельзя',
  'Tarixdan to‘ldirish': 'Заполнить из истории',
  'Bosh kitob to‘ldirildi': 'Главная книга заполнена',
  'ta yozuv': 'записей',
  'Bajarilmoqda...': 'Выполняется...',
  'Buxgalteriya hisoblari — avtomatik postingning manzili':
      'Бухгалтерские счета — куда идёт автоматический разнос',
  'Mavjud savdo/xarajat tarixidan Bosh kitobni to‘ldirish':
      'Заполнить главную книгу из истории продаж/расходов',
  // statements
  'Sof daromad': 'Чистый доход',
  'Tovar tannarxi': 'Себестоимость товара',
  'Yalpi foyda': 'Валовая прибыль',
  'Sof foyda': 'Чистая прибыль',
  'Foyda hisoboti': 'Отчёт о прибыли',
  'Operatsion xarajatlar': 'Операционные расходы',
  'Jami daromad': 'Итого доход',
  'Tanlangan davr uchun daromad, tannarx va xarajatlar (Bosh kitobdan)':
      'Доход, себестоимость и расходы за период (из главной книги)',
  'Aktivlar': 'Активы',
  'Passivlar (majburiyatlar)': 'Пассивы (обязательства)',
  'Jami aktivlar': 'Итого активы',
  'Jami passivlar': 'Итого пассивы',
  'Jami kapital': 'Итого капитал',
  'Joriy davr foydasi': 'Прибыль текущего периода',
  'Balans teng': 'Баланс сходится',
  'Balans teng emas': 'Баланс не сходится',
  'Passiv + Kapital': 'Пассив + Капитал',
  'Aktivlar = Passivlar + Kapital (tanlangan sanaga)':
      'Активы = Пассивы + Капитал (на дату)',
  'Boshlang‘ich qoldiq': 'Начальный остаток',
  'Yakuniy qoldiq': 'Конечный остаток',
  'Kirim': 'Поступления',
  'Chiqim': 'Расход',
  'Pul kirimi': 'Поступление денег',
  'Pul chiqimi': 'Расход денег',
  'Sof o‘zgarish': 'Чистое изменение',
  'Bu davrda harakat yo‘q': 'Нет движений за период',
  'Naqd va bankdagi pul harakati (tanlangan davr)':
      'Движение наличных и банка (за период)',
  // journal
  'Yangi yozuv': 'Новая запись',
  'Yangi jurnal yozuvi': 'Новая запись журнала',
  'Yozuvlar': 'Записи',
  'Manba': 'Источник',
  'Bu davrda yozuv yo‘q': 'Нет записей за период',
  'Ikki tomonlama yozuvlar — avtomatik va qo‘lda':
      'Двойные записи — авто и вручную',
  'Yozuv qo‘shildi': 'Запись добавлена',
  "Yozuvni o'chirish": 'Удалить запись',
  "Yozuv o'chirildi": 'Запись удалена',
  "Faqat qo'lda kiritilgan yozuv o'chiriladi. Davom etamizmi?":
      'Удаляется только запись, внесённая вручную. Продолжить?',
  'Storno (teskari yozuv)': 'Сторно (обратная запись)',
  'Bu yozumni teskari yozuv bilan bekor qilmoqchimisiz?':
      'Отменить эту запись обратной проводкой?',
  'Storno qilish': 'Сделать сторно',
  'Storno yozuvi yaratildi': 'Сторно-запись создана',
  'Tanlang...': 'Выберите...',
  'Qator qo‘shish': 'Добавить строку',
  'Kamida 2 ta to‘ldirilgan qator kerak': 'Нужно минимум 2 заполненные строки',
  'Debet va kredit teng bo‘lishi kerak': 'Дебет и кредит должны быть равны',
  'Yozuv izohi': 'Описание записи',
  // periods
  'Davrni yopish': 'Закрыть период',
  'Davr': 'Период',
  'Oy': 'Месяц',
  'Yopiq': 'Закрыт',
  'Ochiq': 'Открыт',
  'Ochish': 'Открыть',
  'Yopilgan vaqt': 'Время закрытия',
  'Hali davr yopilmagan': 'Периоды ещё не закрыты',
  'Davr yopildi': 'Период закрыт',
  'Davr ochildi': 'Период открыт',
  'Davrni ochish': 'Открыть период',
  "Davrni o'chirish": 'Удалить период',
  "Davr o'chirildi": 'Период удалён',
  'Yopilmoqda...': 'Закрытие...',
  'Yopilgan davrga yangi yozuv kiritib bo‘lmaydi (qulflanadi)':
      'В закрытый период нельзя вносить записи (блокируется)',
  'Bu davrni qayta ochmoqchimisiz? Unga yozuv kiritish mumkin bo‘ladi.':
      'Открыть период снова? В него можно будет вносить записи.',
  "Davr yozuvi o'chiriladi (yozuvlarning o'ziga tegmaydi). Davom etamizmi?":
      'Удалится запись о периоде (сами проводки не трогаются). Продолжить?',
  'Yopilgandan keyin bu oyga tegishli har qanday yozuv bloklanadi.':
      'После закрытия любые проводки за этот месяц блокируются.',
  // navigation / shell
  'Boshqaruv': 'Панель',
  'Menejment': 'Менеджмент',
  'Xarajatlar': 'Расходы',
  "Do'kon xarajatlari": 'Расходы магазина',
  "To'lov": 'Платежи',
  'Buyurtmalar': 'Заказы',
  'Ombor': 'Склад',
  'Mijozlar': 'Клиенты',
  'Qarz': 'Долги',
  'Kalkulyator': 'Калькулятор',
  'Smena tarixi': 'История смен',
  'Smena yopish': 'Закрыть смену',
  'Moliyaviy boshqaruv tizimi': 'Система финансового управления',
  'Smena ochiq': 'Смена открыта',
  'Smena yopiq': 'Смена закрыта',
  "Yorug' mavzu": 'Светлая тема',
  "Qorong'i mavzu": 'Тёмная тема',
  'Til': 'Язык',
  'Platforma': 'Платформа',
  'Operator': 'Оператор',
  'Ochiq': 'Открыто',
  'Yopiq': 'Закрыто',

  // common actions
  'Saqlash': 'Сохранить',
  'Saqlanmoqda...': 'Сохранение...',
  'Bekor qilish': 'Отмена',
  'Bekor': 'Отмена',
  "O'chirish": 'Удалить',
  'Tahrirlash': 'Редактировать',
  "Qo'shish": 'Добавить',
  "+ Qo'shish": '+ Добавить',
  'Yopish': 'Закрыть',
  'Menyuni kengaytirish': 'Развернуть меню',
  "Menyuni yig'ish": 'Свернуть меню',
  'Kengaytirish': 'Развернуть',
  "Yig'ish": 'Свернуть',
  'Orqaga': 'Назад',
  'Qayta urinish': 'Повторить',
  'Tasdiqlash': 'Подтвердить',
  'Qidiruv': 'Поиск',
  'Tozalash': 'Очистить',
  'Jami': 'Итого',
  'Jami USD': 'Итого USD',
  'Sana': 'Дата',
  'Summa': 'Сумма',
  'Chegirma summa (USD)': 'Сумма скидки (USD)',
  'Chiziq chegirmasi (USD)': 'Скидка по строке (USD)',
  'Holat': 'Статус',
  'Turi': 'Тип',
  'Valyuta': 'Валюта',
  "Bo'lim": 'Раздел',

  // date range
  'Bugun': 'Сегодня',
  'Kecha': 'Вчера',
  'Bu hafta': 'Эта неделя',
  'Bu oy': 'Этот месяц',
  'Hammasi': 'Всё',

  // dashboard
  'Bugungi dollar kursi': 'Сегодняшний курс доллара',
  'Markaziy bank': 'Центральный банк',
  "Internetga ulanib bo'lmadi — kursni keyinroq ko'ring":
    'Нет соединения — посмотрите курс позже',
  'ERTALABGI BALANS': 'УТРЕННИЙ БАЛАНС',
  'Taxminiy qoldiq': 'Примерный остаток',
  '✏️ Tahrirlash': '✏️ Изменить',
  'Bugungi xarajat': 'Расход за сегодня',
  'Kassadan': 'Из кассы',
  'Naqddan': 'Наличными',
  'Kartadan': 'Картой',
  "Do'kon xarajati": 'Расход магазина',
  'Umumiy qarz': 'Общий долг',
  'Smenaning xarajatlari': 'Расходы смены',
  'Eng katta xarajatlar': 'Крупнейшие расходы',
  'Bugun hali xarajat kiritilmagan': 'Сегодня расходов ещё нет',
  'Buyurtmalar holati': 'Статус заказов',
  'Bugun keladi': 'Придёт сегодня',
  'Ertaga keladi': 'Придёт завтра',
  'Kelmagan': 'Не прибыло',
  "Buyurtma yo'q": 'Заказов нет',
  'Ertalabgi balansni tahrirlash': 'Изменить утренний баланс',
  'Ertalabgi balans (USD)': 'Утренний баланс (USD)',
  'Bugun ertalab kassaga olib kelingan naqd pul.':
    'Наличные, принесённые в кассу сегодня утром.',
  'Ertalabgi balans yangilandi': 'Утренний баланс обновлён',
  "Balansni to'g'ri kiriting": 'Введите баланс правильно',

  // dashboard widgets (v1.7.0)
  'Sotuvlar oqimi (7 kun)': 'Поток продаж (7 дней)',
  'Tijorat tranzaksiyalari': 'Коммерческие транзакции',
  'Kassa operatsiyalari': 'Операции кассы',
  "Bugun amaliyot yo'q": 'Сегодня нет операций',
  'Sotuv': 'Продажа',

  // expenses
  "Market va do'kon xarajatlarini birgalikda ko'rish":
    'Расходы магазина и точки вместе',
  "Ko'p kiritish": 'Массовый ввод',
  'Jami xarajat': 'Всего расходов',
  "Xarajatlar ro'yxati": 'Список расходов',
  "Bu sana oralig'ida xarajat topilmadi": 'За этот период расходов нет',
  "To'lov turi": 'Способ оплаты',
  'Market': 'Магазин',
  'Yangi xarajat': 'Новый расход',
  'Xarajatni tahrirlash': 'Редактирование расхода',
  "Xarajat qo'shildi": 'Расход добавлен',
  'Xarajat yangilandi': 'Расход обновлён',
  "Xarajat o'chirildi": 'Расход удалён',
  "Xarajatni o'chirish": 'Удалить расход',
  'Naqd': 'Наличные',
  'Kassa': 'Касса',
  'Karta': 'Карта',
  'Aralash': 'Смешанно',
  'Qarzga': 'В долг',
  'Masalan: ijara, svet, internet': 'Например: аренда, свет, интернет',

  // shop expenses
  "Do'kon faoliyati uchun xarajatlar": 'Расходы на работу магазина',
  "Jami do'kon xarajati": 'Всего расходов магазина',
  "Do'kon xarajatlari ro'yxati": 'Список расходов магазина',
  "Bu sana oralig'ida do'kon xarajati topilmadi":
    'За этот период расходов магазина нет',
  "Yangi do'kon xarajati": 'Новый расход магазина',
  "Do'kon xarajatini tahrirlash": 'Редактирование расхода магазина',
  "Do'kon xarajati qo'shildi": 'Расход магазина добавлен',
  "Do'kon xarajati yangilandi": 'Расход магазина обновлён',
  "Do'kon xarajati o'chirildi": 'Расход магазина удалён',
  "Do'kon xarajatini o'chirish": 'Удалить расход магазина',

  // warehouse
  'Mahsulotlar': 'Товары',
  "Mahsulot qo'shing, narx va ombor miqdorini boshqaring, CSV/XLSX orqali yuklang.":
    'Добавляйте товары, управляйте ценой и остатком, импорт через CSV/XLSX.',
  'Toifalar': 'Категории',
  'Import': 'Импорт',
  'Skaner': 'Сканер',
  'Yangi mahsulot': 'Новый товар',
  '+ Yangi mahsulot': '+ Новый товар',
  'Mahsulot turlari': 'Видов товара',
  'Ombor qiymati (kelish)': 'Стоимость склада (закуп)',
  'Potensial foyda': 'Потенциальная прибыль',
  'Nomi yoki IMEI...': 'Название или IMEI...',
  'Toifa': 'Категория',
  'Barcha toifalar': 'Все категории',
  'Barcha holatlar': 'Все статусы',
  'Mavjud': 'В наличии',
  'Kam qoldi': 'Мало осталось',
  'Tugagan': 'Закончился',
  "Mahsulotlar ro'yxati": 'Список товаров',
  'Mahsulot topilmadi': 'Товары не найдены',
  'Narx': 'Цена',
  'Miqdor': 'Количество',
  "Asosiy ma'lumotlar": 'Основные данные',
  'Kelish narxi (USD) *': 'Цена закупа (USD) *',
  'Sotilish narxi (USD) *': 'Цена продажи (USD) *',
  "Kelish narxi (so'm)": 'Цена закупа (сум)',
  "Sotilish narxi (so'm, ixtiyoriy)": 'Цена продажи (сум, опц.)',
  "Kelish narxi (so'm) *": 'Цена закупа (сум) *',
  "Sotilish narxi (so'm) *": 'Цена продажи (сум) *',
  'Narx valyutasi': 'Валюта цены',
  "so'mda kiritilgan narx kurs bo'yicha USDga aylantirib saqlanadi":
    'цена в сумах сохраняется в USD по курсу',
  'Dollar kursi yuklanmadi — narxlar hozircha faqat USDda kiritiladi':
    'Курс доллара не загружен — цены пока вводятся только в USD',
  'Dollar kursi yuklanmadi — narxni hozircha USDda kiriting':
    'Курс доллара не загружен — введите цену пока в USD',
  'Kelish narxi juda kichik — USDga aylantirilganda $0 bo\'lib qoladi':
    'Цена закупа слишком мала — при конвертации в USD получается $0',
  "Server javob bermadi — sotuv o'tgan-o'tmaganini Cheklar tarixidan tekshiring":
    'Сервер не ответил — проверьте в истории чеков, прошла ли продажа',
  'Shtrix kod': 'Штрих-код',
  'Tanlanmagan': 'Не выбрано',
  "Boshlang'ich miqdor (dona)": 'Начальное количество (шт.)',
  'Tavsif': 'Описание',
  'Mahsulot xususiyatlarini yozing...': 'Опишите характеристики товара...',
  'Ogohlantirish chegarasi': 'Порог предупреждения',
  'Past stok chegarasi': 'Порог низкого остатка',
  "Ombor o'zgarishi": 'Движение склада',
  'Sabab': 'Причина',
  "Qo'llash": 'Применить',
  'Ombor harakatlari': 'Движения склада',
  "Hech qanday harakat yo'q.": 'Движений нет.',
  'Mahsulot nomi kiritilishi shart': 'Введите название товара',
  "Mahsulot qo'shildi": 'Товар добавлен',
  'Saqlandi': 'Сохранено',
  "Mahsulotni o'chirish": 'Удалить товар',

  // customers
  'Mijozlar bazasi, berilgan tovarlar va qarz / balans':
    'База клиентов, выданные товары и долг / баланс',
  'Yangi mijoz': 'Новый клиент',
  '+ Yangi mijoz': '+ Новый клиент',
  'Jami mijozlar': 'Всего клиентов',
  'Mijozlar qarzi': 'Долг клиентов',
  'Bizdagi balans': 'Баланс у нас',
  'Ism yoki telefon raqami...': 'Имя или номер телефона...',
  "Mijozlar ro'yxati": 'Список клиентов',
  'Mijoz topilmadi': 'Клиенты не найдены',
  'Ism': 'Имя',
  'Telefon': 'Телефон',
  'Manzil': 'Адрес',
  'Mijoz qarzi': 'Долг клиента',
  'Bizda qolgan balans': 'Остаток у нас',
  'Hisob teng': 'Баланс равный',
  'Qarz': 'Долг',
  'Balans': 'Баланс',
  'Teng': 'Ноль',
  'Mijozni tahrirlash': 'Редактирование клиента',
  "Mijoz qo'shildi": 'Клиент добавлен',
  'Mijoz yangilandi': 'Клиент обновлён',
  "Mijoz o'chirildi": 'Клиент удалён',
  'Ism *': 'Имя *',
  'Mijozning ism-familiyasi': 'Имя и фамилия клиента',
  'Telefon raqami': 'Номер телефона',
  "Shahar, ko'cha, uy": 'Город, улица, дом',
  'Mijoz ismi kiritilishi shart': 'Введите имя клиента',
  'Berilgan tovarlar': 'Выданные товары',
  "To'langan": 'Оплачено',
  'Amallar tarixi': 'История операций',
  'Tovar berish': 'Выдать товар',
  "To'lov olish": 'Принять оплату',
  'Amal': 'Операция',
  'Tovar': 'Товар',
  "Mijoz ma'lumotlari": 'Данные клиента',
  "Mijozni o'chirish": 'Удалить клиента',
  "To'lov qabul qilish": 'Принять оплату',
  'Tovar nomi *': 'Название товара *',
  'Narxi (USD)': 'Цена (USD)',
  'Tovar nomini kiriting': 'Введите название товара',
  'Tovar berildi': 'Товар выдан',
  "To'lov qabul qilindi": 'Оплата принята',
  "Amalni o'chirish": 'Удалить операцию',

  // management
  'Savdo hajmi ombordan avtomatik, xarajatlar va sof foyda':
    'Объём продаж со склада, расходы и чистая прибыль',
  "Xarajat qo'shish": 'Добавить расход',
  "+ Xarajat qo'shish": '+ Добавить расход',
  'Savdo hajmi': 'Объём продаж',
  'Tovar tannarxi': 'Себестоимость товара',
  'Yalpi foyda': 'Валовая прибыль',
  'Sotilgan dona': 'Продано штук',
  'Ishchi oyliklari': 'Зарплаты работников',
  'Soliqlar': 'Налоги',
  'Boshqa xarajatlar': 'Прочие расходы',
  'Sof foyda': 'Чистая прибыль',
  'Foyda hisoboti': 'Отчёт о прибыли',
  'Bu davrda xarajat kiritilmagan': 'За этот период расходов нет',
  'Ishchi oyligi': 'Зарплата работника',
  'Soliq': 'Налог',
  'Boshqa xarajat': 'Прочий расход',
  'Xarajat turi': 'Тип расхода',
  'Ishchi ismi *': 'Имя работника *',
  'Masalan: Akmal': 'Например: Акмал',
  'Xarajat nomi': 'Название расхода',

  // sold-goods export
  'Sotilgan tovarlar': 'Проданные товары',
  "Tanlangan davr uchun sotilgan tovarlar ro'yxatini yuklab oling":
    'Скачайте список проданных товаров за выбранный период',
  'Sotilgan tovarlar hisoboti': 'Отчёт о проданных товарах',
  'Mahsulot': 'Товар',
  'Soni': 'Количество',
  'Sotuv narxi': 'Цена продажи',
  'Tan narxi': 'Себестоимость',
  'Foyda': 'Прибыль',
  'Izoh': 'Примечание',
  'Davr': 'Период',
  "Bu davrda sotilgan tovar yo'q": 'За этот период проданных товаров нет',
  'Tayyorlanmoqda...': 'Подготовка...',
  'Hisobot sanasi': 'Дата отчёта',

  // product tax fields (IKPU / QQS / unit)
  "Soliq ma'lumotlari": 'Налоговые данные',
  "Elektron faktura va onlayn-kassa uchun (ixtiyoriy). Bu ma'lumotlar hech qayerga yuborilmaydi — integratsiya yoqilmaguncha shunchaki saqlanadi.":
    'Для электронной фактуры и онлайн-кассы (необязательно). Эти данные никуда не отправляются — просто хранятся, пока интеграция не включена.',
  'IKPU / MXIK kodi': 'Код ИКПУ / MXIK',
  'Milliy katalog kodi': 'Код национального каталога',
  'QQS stavkasi': 'Ставка НДС',
  'Belgilanmagan': 'Не указано',
  "O'lchov birligi": 'Единица измерения',
  'kg': 'кг',
  'litr': 'литр',
  'metr': 'метр',
  'quti': 'коробка',
  "to'plam": 'комплект',
  "Yashirish": 'Скрыть',
  "Ko'rsatish": 'Показать',
  'Server sozlamalari': 'Настройки сервера',
  'License Server URL': 'License Server URL',

  // payments
  "To'lovlar jurnali — barcha pul harakatlari":
    'Журнал платежей — все движения денег',
  "Yangi to'lov": 'Новый платёж',
  "+ Yangi to'lov": '+ Новый платёж',
  'Kirim': 'Приход',
  'Chiqim': 'Расход',
  'Sof oqim': 'Чистый поток',
  "To'lovlar jurnali": 'Журнал платежей',
  "Bu davrda to'lov yozuvi yo'q": 'За этот период платежей нет',
  "Yo'nalish": 'Направление',
  'Kim': 'Кто',
  'Usul': 'Способ',
  "Mijoz to'lovi": 'Оплата клиента',
  'Yetkazib beruvchiga': 'Поставщику',
  'Ish haqi': 'Зарплата',
  'Boshqa': 'Прочее',
  "To'lovni tahrirlash": 'Редактирование платежа',
  "To'lov qo'shildi": 'Платёж добавлен',
  "To'lov yangilandi": 'Платёж обновлён',
  "To'lov o'chirildi": 'Платёж удалён',
  "To'lovni o'chirish": 'Удалить платёж',
  "Bu to'lov yozuvini o'chirmoqchimisiz?": 'Удалить эту запись платежа?',
  'Kim (ixtiyoriy)': 'Кто (необязательно)',
  'Mijoz / yetkazib beruvchi / ishchi ismi':
    'Имя клиента / поставщика / работника',
  "To'lov usuli": 'Способ оплаты',

  // calculator
  'Hisob-kitob va valyuta konvertori': 'Расчёты и конвертер валют',
  'Valyuta konvertori': 'Конвертер валют',
  'AQSh dollari (USD)': 'Доллар США (USD)',
  "So'm (UZS)": 'Сум (UZS)',
  "Telefon narxlari dollarda — mijozga so'mdagi summasini tez ayting.":
    'Цены в долларах — быстро назовите клиенту сумму в сумах.',

  // debt
  'Mening qarzlarim va bizdan qarzlar': 'Мои долги и долги нам',
  'Mening qarzlarim': 'Мои долги',
  'Bizdan qarzlar': 'Долги нам',
  '+ Qarz': '+ Долг',
  "Qarz yo'q": 'Долгов нет',
  "To'lash": 'Оплатить',
  "+ Qo'sh": '+ Долож.',
  'Tarix': 'История',
  'Kimdan olindi': 'У кого взято',
  'Mijoz ismi': 'Имя клиента',
  'Tovar / sabab (ixtiyoriy)': 'Товар / причина (необязательно)',
  "Qarzni to'lash": 'Оплата долга',
  "Qarzga qo'shish": 'Добавить к долгу',
  'Qarz tarixi': 'История долга',
  "Tarix bo'sh": 'История пуста',
  "Qarz qo'shildi": 'Долг добавлен',
  'Qarz yangilandi': 'Долг обновлён',
  "Qarz o'chirildi": 'Долг удалён',
  "Qarzni o'chirish": 'Удалить долг',

  // scan / import
  'Shtrix kod skaneri': 'Сканер штрих-кода',
  'Skanerlangan': 'Отсканировано',
  'Soni (dona)': 'Количество (шт.)',
  "Omborga qo'shish": 'Добавить на склад',
  'Kelish narxi (USD)': 'Цена закупа (USD)',
  'Sotilish narxi (USD)': 'Цена продажи (USD)',

  // give-goods picker (customer detail)
  "Mijozga berilgan tovar ombordan ayiriladi va qarziga qo'shiladi.":
    'Выданный товар списывается со склада и добавляется к долгу клиента.',
  "Bir nechta mahsulot qo'shib, bir marta saqlang. Tovarlar ombordan ayiriladi.":
    'Добавьте несколько товаров и сохраните разом. Товары списываются со склада.',
  "Savatga qo'shish": 'Добавить в корзину',
  'Tovarlar berildi': 'Товары выданы',
  'Omborda yetarli emas': 'На складе недостаточно',
  "Kamida bitta tovar qo'shing": 'Добавьте хотя бы один товар',
  'Kamida bitta tovar belgilang': 'Отметьте хотя бы один товар',
  'Kerakli tovarlarni belgilang va sonini tanlang. Tovarlar ombordan ayiriladi.':
    'Отметьте нужные товары и укажите количество. Товары списываются со склада.',
  'Mahsulot — nomi yoki shtrix kod': 'Товар — название или штрих-код',
  'Skanerlang yoki nomini yozing...': 'Отсканируйте или введите название...',
  'Mavjud tovar topilmadi': 'Доступный товар не найден',
  'Qoldiq': 'Остаток',
  'dona': 'шт.',
  'Boshqa tovar': 'Другой товар',
  'Narxi — jami (USD)': 'Цена — итого (USD)',
  'Mahsulot tanlang yoki skanerlang': 'Выберите или отсканируйте товар',
  "Narx musbat bo'lishi kerak": 'Цена должна быть положительной',
  'Bu tovar omborda topilmadi': 'Этот товар не найден на складе',
  'bu tovar qolmagan': 'этот товар закончился',
  'Summa (USD)': 'Сумма (USD)',
  "Masalan: naqd to'lov": 'Например: наличная оплата',
  'Eslatma (ixtiyoriy)': 'Заметка (необязательно)',

  // edit ledger line
  'Amalni tahrirlash': 'Редактирование операции',
  'Amal yangilandi': 'Операция обновлена',
  'Tovar nomi': 'Название товара',
  "Yozuvni to'g'rilash — ombor qoldig'iga ta'sir qilmaydi.":
    'Исправление записи — не влияет на остаток склада.',

  // validation / errors
  'Nomi kiritilishi shart': 'Введите название',
  "Summa musbat bo'lishi kerak": 'Сумма должна быть положительной',
  'Xatolik yuz berdi': 'Произошла ошибка',
  "Sotilish narxi 0 dan katta bo'lishi kerak":
    'Цена продажи должна быть больше 0',
  'Diqqat: sotilish narxi kelish narxidan past':
    'Внимание: цена продажи ниже цены закупа',

  // debt page (v1.7.x redesign)
  'Qarz Daftari & Audit': 'Долговая книга и аудит',
  "Mijozlar debitorligi, ta'minotchilar majburiyatlari va risk tahlili":
    'Дебиторка клиентов, обязательства перед поставщиками и анализ риска',
  'Yangi Qarz Yozuvi': 'Новая запись долга',
  'Mening qarzim': 'Мой долг',
  'Bizdan Qarzlar': 'Долги нам',
  'Mening Qarzlarim': 'Мои долги',
  'JAMI KUTILAYOTGAN TUSHUM': 'ИТОГО ОЖИДАЕМЫЙ ПРИХОД',
  'JAMI MAJBURIYATLAR': 'ИТОГО ОБЯЗАТЕЛЬСТВА',
  "MOLIYAVIY SOG'LOMLIK": 'ФИНАНСОВОЕ ЗДОРОВЬЕ',
  'Kredit risk reytingi': 'Кредитный рейтинг риска',
  'Holat:': 'Состояние:',
  "A'LO (Xavfsiz)": 'ОТЛИЧНО (Безопасно)',
  "O'rtacha": 'Среднее',
  'Xavfli': 'Опасно',
  "Bo'sh": 'Пусто',
  'yozuv': 'запись',
  'faol': 'активн.',
  "Bu yo'nalishda faol qarz yo'q": 'В этом направлении активных долгов нет',
  'Yopilgan': 'Закрыт',
  'Faol': 'Активен',
  "To'langan qism": 'Оплаченная часть',
  'Qoldiq': 'Остаток',
  'Umumiy summa': 'Общая сумма',
  "Tezkor to'lov": 'Быстрая оплата',
  "To'liq yopish": 'Полное закрытие',
  'Mijoz': 'Клиент',
  'MIJOZ LEDGER': 'КЛИЕНТ',
  'Tovar ledger balansi': 'Баланс товарного журнала',
  'Tafsilot → Mijoz sahifasi': 'Подробнее → Страница клиента',
  "To'lov qabul qilish: mijoz sahifasidan": 'Принять оплату — со страницы клиента',

  // payment methods + click-to-add
  'Yangi kirim': 'Новый приход',
  'Yangi chiqim': 'Новый расход',
  "Kirim qo'shish": 'Добавить приход',
  "Chiqim qo'shish": 'Добавить расход',
  "Yangi kirim qo'shish": 'Добавить новый приход',
  "Yangi chiqim qo'shish": 'Добавить новый расход',
  "UZS (so'm)": 'UZS (сум)',
  'USD (dollar)': 'USD (доллар)',
  'Karta (P2P)': 'Карта (P2P)',
  'Transfer': 'Перечисление',
  "G'AZNA TAHLILI": 'АНАЛИЗ КАЗНЫ',
  "4 xil to'lov usuli bo'yicha sof qoldiq":
    'Чистый остаток по 4 способам оплаты',
  'Sof oqim:': 'Чистый поток:',
  'Naqd pul': 'Наличные',
  "Valyuta g'aznasi": 'Валютная казна',
  "Plastik o'tkazmalar": 'Карточные переводы',
  'Bank hisobi (yuridik)': 'Банковский счёт (юр.)',

  // payment journal source tags (cross-page integration)
  'XARAJAT': 'РАСХОД',
  "DO'KON": 'МАГАЗИН',
  'MIJOZ': 'КЛИЕНТ',
  'Xarajatlar sahifasidan': 'Со страницы Расходы',
  "Do'kon xarajatlari sahifasidan": 'Со страницы Расходы магазина',
  "Mijoz to'lovi (ledger)": 'Оплата клиента (журнал)',
  'Manba sahifasidan tahrirlang': 'Редактируйте на исходной странице',
  "Bosing — yangi yozuv qo'shish": 'Нажмите — добавить новую запись',
  "MAVJUD PUL MABLAG'LARI": 'ДОСТУПНЫЕ ДЕНЕЖНЫЕ СРЕДСТВА',
  'Jami:': 'Итого:',
  'Mijoz ismi (mavjudlardan tanlash mumkin)':
    'Имя клиента (можно выбрать из существующих)',

  // customer detail day-grouped ledger + nakladnoy printing
  'Tovar berildi': 'Товар выдан',
  "To'lov qabul qilindi": 'Оплата принята',
  'Nakladnoy chop etish': 'Печать накладной',
  'Chop etish': 'Печать',
  'Saqlab nakladnoy chop etish': 'Сохранить и распечатать накладную',
  'Saqlash + chop etish': 'Сохранить + печать',
  'ta tovar': 'товар(ов)',
  'Tovar nomi': 'Название товара',
  "Nakladnoy ko'rinishi": 'Просмотр накладной',
  "80mm chek qog'oz uchun. Tekshiring va printerga yuboring.":
    'Для чека 80мм. Проверьте и отправьте на печать.',
  'Printerga yuborish': 'Отправить на печать',
  "Nakladnoyni ko'rish va chop etish": 'Просмотреть и распечатать накладную',
  'Yetkazib beruvchi ismi (avval kiritilganlardan tanlash mumkin)':
    'Имя поставщика (можно выбрать из ранее добавленных)',
  'Ishchi ismi (avval kiritilganlardan tanlash mumkin)':
    'Имя работника (можно выбрать из ранее добавленных)',
  'Smartfonlar': 'Смартфоны',
  'Noutbuklar': 'Ноутбуки',
  'Elektronika': 'Электроника',

  // suppliers feature
  'Yetkazib beruvchilar': 'Поставщики',
  'YETKAZIB BERUVCHILAR': 'ПОСТАВЩИКИ',
  "Yetkazib beruvchilar bo'limiga o'tish": 'Перейти к разделу поставщиков',
  "Yetkazib beruvchilar kontaktlari, oldindan to'langan mablag'lar":
    'Контакты поставщиков и предоплаченные средства',
  'Yangi yetkazib beruvchi': 'Новый поставщик',
  'Yetkazib beruvchini tahrirlash': 'Редактирование поставщика',
  'Jami yetkazib beruvchilar': 'Всего поставщиков',
  "Ularga to'langan": 'Им выплачено',
  "Hali yetkazib beruvchi yo'q": 'Поставщиков ещё нет',
  "Yetkazib beruvchilar ro'yxati": 'Список поставщиков',
  "Yetkazib beruvchi qo'shildi": 'Поставщик добавлен',
  "Yetkazib beruvchi o'chirildi": 'Поставщик удалён',
  "Yetkazib beruvchini o'chirish": 'Удалить поставщика',
  "Bu yetkazib beruvchini o'chirmoqchimisiz? To'lov tarixi saqlanadi.":
    'Удалить этого поставщика? История платежей сохранится.',
  "Bu yetkazib beruvchini o'chirmoqchimisiz?": 'Удалить этого поставщика?',
  "Yetkazib beruvchi ma'lumotlari": 'Данные поставщика',
  'Yetkazib beruvchi ismi kiritilishi shart': 'Введите имя поставщика',
  'Yetkazib beruvchi ismi yoki kompaniya': 'Имя поставщика или компания',
  "To'lov tarixi": 'История платежей',
  "Hali to'lov yo'q — To'lov sahifasida yetkazib beruvchiga to'lov yarating":
    'Платежей пока нет — создайте платёж поставщику на странице «Платежи»',
  "To'langan jami": 'Всего оплачено',
  'Yozuvlar soni': 'Записей',
  'Yangilandi': 'Обновлено',
  "Tarixni CSV ko'rinishida yuklab olish": 'Скачать историю в формате CSV',
  "Yetkazib beruvchi ismi (ro'yxatdan tanlang yoki yozing)":
    'Имя поставщика (выберите из списка или введите)',
  "Ro'yxatda": 'В списке',
  "Avval kiritilgan": 'Ранее введено',

  // auth (Phase 1A)
  'Tizimga kirish': 'Вход в систему',
  'Akkauntingiz uchun login va parolni kiriting':
    'Введите логин и пароль для вашего аккаунта',
  'Login': 'Логин',
  'Parol': 'Пароль',
  'Kirish': 'Войти',
  'Login va parolni kiriting': 'Введите логин и пароль',
  'Login muvaffaqiyatsiz': 'Вход не выполнен',
  'Tekshirilmoqda...': 'Проверка...',
  "Parol unutilgan bo'lsa super-admin bilan bog'laning":
    'Если пароль забыт — свяжитесь с супер-админом',
  'Chiqish': 'Выйти',
  'Obuna muddati tugashiga': 'До истечения подписки осталось',
  'kun qoldi': 'дн.',
  "To'lov muddati": 'Срок оплаты',
  "To'lamasangiz akkaunt avtomatik bloklanadi.":
    'Если не оплатите — аккаунт автоматически заблокируется.',

  // super-admin panel (Phase 1B)
  'Super-admin': 'Супер-админ',
  'Super-admin panel': 'Панель супер-админа',
  'Mijoz akkauntlari, obunalar, parollar boshqaruvi':
    'Управление аккаунтами, подписками, паролями',
  'Yangi akkaunt': 'Новый аккаунт',
  'Yangi akkaunt yaratish': 'Создать новый аккаунт',
  'Jami akkauntlar': 'Всего аккаунтов',
  'Bloklangan / muddati tugagan': 'Заблокирован / истёк',
  "Akkauntlar ro'yxati": 'Список аккаунтов',
  "Hozircha akkaunt yo'q": 'Аккаунтов пока нет',
  'Akkaunt': 'Аккаунт',
  'Obuna tugashi': 'Срок подписки',
  'Foydalanuvchilar': 'Пользователи',
  'Bloklangan': 'Заблокирован',
  'Muddati tugagan': 'Срок истёк',
  'Akkauntni tahrirlash': 'Редактировать аккаунт',
  'Akkauntni bloklash': 'Заблокировать',
  'Akkauntni ochish': 'Разблокировать',
  "Akkauntni o'chirish": 'Удалить аккаунт',
  'Akkaunt yaratildi': 'Аккаунт создан',
  'Akkaunt yangilandi': 'Аккаунт обновлён',
  'Akkaunt bloklandi': 'Аккаунт заблокирован',
  'Akkaunt ochildi': 'Аккаунт разблокирован',
  "Akkaunt o'chirildi": 'Аккаунт удалён',
  'Akkaunt nomi *': 'Название аккаунта *',
  'Akkaunt nomi kiritilishi shart': 'Введите название аккаунта',
  'Akkaunt nomi, login va parolni kiriting':
    'Заполните название аккаунта, логин и пароль',
  "Bu akkauntni va barcha foydalanuvchilarini o'chirmoqchimisiz?":
    'Удалить этот аккаунт и всех его пользователей?',
  "Yangi mijoz akkaunti yaratiladi va birinchi foydalanuvchi (owner) qo'shiladi.":
    'Будет создан новый аккаунт клиента и первый пользователь (owner).',
  'Obuna muddati': 'Срок подписки',
  '30 kun (1 oy)': '30 дней (1 мес.)',
  '60 kun (2 oy)': '60 дней (2 мес.)',
  '90 kun (3 oy)': '90 дней (3 мес.)',
  '180 kun (6 oy)': '180 дней (6 мес.)',
  '365 kun (1 yil)': '365 дней (1 год)',
  'Cheksiz (test)': 'Без срока (тест)',
  'Cheksiz': 'Без срока',
  'Owner (birinchi foydalanuvchi)': 'Owner (первый пользователь)',
  'Ism-familiya (ixtiyoriy)': 'Имя и фамилия (опц.)',
  'Yaratish': 'Создать',
  'Yangi foydalanuvchi': 'Новый пользователь',
  "Foydalanuvchi yo'q": 'Пользователей нет',
  'Oxirgi kirish': 'Последний вход',
  'Role': 'Роль',
  'Ism-familiya': 'Имя и фамилия',
  "Parolni o'zgartirish": 'Сменить пароль',
  'Yangi parol': 'Новый пароль',
  "O'zgartirish": 'Изменить',
  'Parol yangilandi': 'Пароль обновлён',
  "Foydalanuvchini o'chirishni tasdiqlaysizmi?":
    'Подтвердить удаление пользователя?',
  "Foydalanuvchi o'chirildi": 'Пользователь удалён',
  "Foydalanuvchi qo'shildi": 'Пользователь добавлен',
  'Kamida 4 ta belgi kiriting': 'Введите минимум 4 символа',
  'kun': 'дн.',

  // multi-shop (Phase 1C-1)
  "Do'konlar": 'Магазины',
  "Faol do'kon": 'Активный магазин',
  "Do'kon tanlanmagan": 'Магазин не выбран',
  'ASOSIY': 'ОСНОВНОЙ',
  "Do'konni tanlang": 'Выберите магазин',
  "Akkauntingizdagi do'konlarni boshqarish":
    'Управление магазинами вашего аккаунта',
  "Yangi do'kon": 'Новый магазин',
  "Do'kon yo'q": 'Магазинов нет',
  "Do'kon yaratildi": 'Магазин создан',
  "Do'kon yangilandi": 'Магазин обновлён',
  "Do'kon o'chirildi": 'Магазин удалён',
  "Do'konni tahrirlash": 'Редактировать магазин',
  "Do'konni o'chirish": 'Удалить магазин',
  "Asosiy do'kon o'zgartirildi": 'Основной магазин изменён',
  "Asosiy qil": 'Сделать основным',
  "Do'kon nomi *": 'Название магазина *',
  "Do'kon nomi kiritilishi shart": 'Введите название магазина',
  "Ushbu do'konni o'chirmoqchimisiz?": 'Удалить этот магазин?',
  "Hozir do'kon switcher faollashtirildi va har bir do'kon alohida sub-tenant sifatida saqlanmoqda. Keyingi versiyada har do'kon o'z mahsuloti, mijozi, kassasi bilan to'liq ajratiladi.":
    'Сейчас активирован переключатель магазинов; каждый магазин хранится как отдельный суб-тенант. В следующей версии каждый магазин будет полностью изолирован: товары, клиенты, касса.',
  'Manzil': 'Адрес',

  // consolidated view (Phase 1C-3)
  "Hamma do'konlar": 'Все магазины',
  'Jami balans va sotuvlarni jamlaydi': 'Суммирует общий баланс и продажи',
  "Hamma do'konlar rejimi faol": 'Режим "Все магазины" активен',
  "Bu yerda barcha": 'Здесь собраны данные всех',
  "ta do'konning ma'lumotlari jamlangan. Yangi mahsulot/mijoz/to'lov qo'shish uchun aniq do'konni tanlang.":
    " магазинов. Чтобы добавить товар/клиента/платёж — выберите конкретный магазин.",

  // ---------- Phase 5: new modules / panels (TIER 1 + TIER 2) ----------
  // Account modules editor
  'Sidebar modullari va sozlamalar': 'Модули и настройки боковой панели',
  "Akkaunt sozlamalari": 'Настройки аккаунта',
  "Akkauntlar ro'yxati": 'Список аккаунтов',
  "Modullar": 'Модули',
  "Hammasini yoqish": 'Включить все',
  "Hammasini o'chirish": 'Выключить все',
  "Belgilangan modullar shu akkauntning barcha foydalanuvchilari uchun chap menyuda ko'rinadi. Hech narsa belgilanmasa — hammasi ko'rinadi (legacy default).":
    'Отмеченные модули показываются всем пользователям этого аккаунта в боковом меню. Если ничего не отмечено — видны все (legacy default).',
  "Saqlandi — foydalanuvchilar keyingi kirishda yangi sozlamani ko'radi":
    'Сохранено — пользователи увидят новые настройки при следующем входе',
  "Saqlab bo'lmadi": 'Не удалось сохранить',
  "Foydalanuvchilar": 'Пользователи',
  "Oxirgi kirish": 'Последний вход',

  // Audit log
  'Audit log': 'Журнал аудита',
  "Super-admin amallar tarixi — append-only, oxirgisi tepada":
    'История действий супер-админа — только добавление, новейшее сверху',
  "Audit log bo'sh": 'Журнал пуст',
  'Vaqt': 'Время',
  'Amal': 'Действие',
  'Obyekt': 'Объект',
  'Tafsilot': 'Детали',
  'IP': 'IP',
  'Yangilash': 'Обновить',
  'Yana yuklash': 'Загрузить ещё',
  'Yuklanmoqda...': 'Загрузка...',

  // Low stock widget
  "Past stok ogohlantirishi": 'Предупреждение: мало товара',
  "tovar tugash arafasida": 'товар(ов) заканчиваются',
  "Omborni tekshirish": 'Проверить склад',
  "Hammasi yetarli": 'Всего достаточно',

  // Reports / new tabs
  'Mahsulot bo\'yicha foyda': 'Прибыль по товарам',
  'Soatlik sotuvlar': 'Продажи по часам',
  'Eng foydali mahsulotlar': 'Самые прибыльные товары',
  'Sekin sotilayotgan tovarlar': 'Медленно продающиеся товары',
  'Foyda': 'Прибыль',
  'Sotilgan dona': 'Продано шт.',

  // Price tags PDF
  'Narx yorliqlari': 'Ценники',
  'Yorliq chiqarish': 'Печать ценников',
  'Tanlangan mahsulotlar': 'Выбранные товары',

  // Refund workflow
  'Qaytarish': 'Возврат',
  'Chek raqami': 'Номер чека',
  'Qaytarish sababi': 'Причина возврата',
  "Qisman qaytarish": 'Частичный возврат',
  "To'liq qaytarish": 'Полный возврат',
  "Sotuvni topish": 'Найти продажу',

  // Discounts
  'Chegirma': 'Скидка',
  'Chegirma %': 'Скидка %',
  'Chegirma summa': 'Скидка сумма',
  "Chegirma qo'llanildi": 'Скидка применена',

  // Excel export
  'Excel\'ga eksport': 'Экспорт в Excel',
  "Eksport qilinmoqda...": 'Экспорт...',
  'Eksport tayyor': 'Экспорт готов',

  // Quick search (Ctrl+K)
  "Tezkor qidiruv": 'Быстрый поиск',
  "Sahifa, mahsulot, mijoz...": 'Страница, товар, клиент...',
  "Hech narsa topilmadi": 'Ничего не найдено',
  "Sahifalar": 'Страницы',
  "Mahsulotlar": 'Товары',
  "Mijozlar": 'Клиенты',

  // Auto-backup
  'Backup yaratildi': 'Резервная копия создана',
  "Oxirgi backup": 'Последняя копия',
};

/** Translates an Uzbek-Latin string into the given language. */
export function translate(lang, text) {
  if (text == null || text === '') {
    return text;
  }
  if (lang === 'ru') {
    return RU[text] ?? text;
  }
  if (lang === 'uzc') {
    return toCyrillic(text);
  }
  return text;
}
