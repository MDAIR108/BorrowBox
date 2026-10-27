/**
 * KUET Configuration & Roll Number Validation
 *
 * Roll number format: 7 digits
 * [2-digit batch] + [2-digit department code] + [3-digit serial]
 * Example: '2307108'
 *  - Batch: 23 (2023)
 *  - Dept:  07 (Computer Science & Engineering)
 *  - Serial: 108
 */

const KUET_CONFIG = {
  // 7-digit pattern: 2 digits batch + 2 digits dept + 3 digits serial
  ROLL_REGEX: /^([0-9]{2})([0-9]{2})([0-9]{3})$/,

  // Official KUET Department 2-digit codes
  DEPARTMENT_CODES: {
    '01': 'Civil Engineering (CE)',
    '03': 'Electrical & Electronic Engineering (EEE)',
    '05': 'Mechanical Engineering (ME)',
    '07': 'Computer Science & Engineering (CSE)',
    '09': 'Chemical Engineering (ChE)',
    '11': 'Industrial & Production Engineering (IPE)',
    '13': 'Glass & Ceramic Engineering (GCE)',
    '15': 'Urban & Regional Planning (URP)',
    '17': 'Biomedical Engineering (BME)',
    '19': 'Architecture (Arch)',
    '21': 'Materials Science & Engineering (MSE)',
    '23': 'Energy Science & Engineering (ESE)',
    '25': 'Mechatronics Engineering (MTE)',
    '27': 'Textile Engineering (TE)'
  },

  // KUET Student Email domain
  EMAIL_DOMAINS: [
    'stud.kuet.ac.bd',
    'kuet.ac.bd'
  ],

  // KUET Residential Halls
  HALLS: [
    'Amar Ekushey Hall',
    'Bangabandhu Sheikh Mujibur Rahman Hall',
    'Fazlul Haque Hall',
    'Lalan Shah Hall',
    'Khan Jahan Ali Hall',
    'Dr. M. A. Rashid Hall',
    'Rokeya Hall'
  ]
};

/**
 * Validates a KUET roll number.
 * @param {string} roll - e.g. "2307108"
 * @returns {{ isValid: boolean, error?: string, details?: { batch: string, deptCode: string, deptName: string, serial: string } }}
 */
function validateKuetRoll(roll) {
  if (!roll || typeof roll !== 'string') {
    return { isValid: false, error: 'Roll number is required.' };
  }

  const cleanRoll = roll.trim();
  const match = cleanRoll.match(KUET_CONFIG.ROLL_REGEX);

  if (!match) {
    return {
      isValid: false,
      error: 'Invalid KUET roll format. Must be 7 digits (e.g. 2307108: 2-digit batch, 2-digit dept code, 3-digit serial).'
    };
  }

  const [, batch, deptCode, serial] = match;
  const deptName = KUET_CONFIG.DEPARTMENT_CODES[deptCode];

  if (!deptName) {
    return {
      isValid: false,
      error: `Invalid department code "${deptCode}" in roll number. Valid codes: ${Object.keys(KUET_CONFIG.DEPARTMENT_CODES).join(', ')}.`
    };
  }

  return {
    isValid: true,
    details: { batch, deptCode, deptName, serial }
  };
}

module.exports = {
  KUET_CONFIG,
  validateKuetRoll
};
