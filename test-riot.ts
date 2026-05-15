async function test() {
  const r = await fetch('https://euw1.api.riotgames.com/lol/league/v4/entries/by-summoner/');
  console.log(r.status);
}
test();
