import fs from 'fs';
fetch("http://localhost:3000/api/generate", {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ nickname: 'Faker' })
}).then(r => r.arrayBuffer()).then(b => fs.writeFileSync('test_card.png', Buffer.from(b)));
