package com.lazify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LegacyDenickSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger("lazify");
    private static final String SUPERSTAR_URL = "https://bordic.xyz/api/v2/resources/superstar";
    private static final long PROFILE_CACHE_MS = TimeUnit.MINUTES.toMillis(15);
    private static final long SUPERSTAR_CACHE_MS = TimeUnit.MINUTES.toMillis(15);
    private static final int REQUEST_TIMEOUT_SECONDS = 8;
    private static final Pattern STAR_BRACKET = Pattern.compile("\\[(\\d+)\\??\\]");
    private static final Pattern FINALS_IN_MSG = Pattern.compile("'s final #(\\d+)");
    private static final Pattern BEDS_IN_MSG = Pattern.compile("Bed was bed #(\\d+) destroyed");
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Set<String> NICK_SKIN_HASHES = Set.of(
            "4c7b0468044bfecacc43d00a3a69335a834b73937688292c20d3988cae58248d",
            "3b60a1f6d562f52aaebbf1434f1de147933a3affe0e764fa49ea057536623cd3",
            "19875bb4ac8e7e68c122fdf22bf99abeb4326b96c58ec21d4c5b64cc7a12a5",
            "dd2f967eee43908cda7854df9eb7263637573fd10e498dcdf5d60e9ebc80a1e5",
            "21c44f6b47eadd6720ddc1a14dc4502bd6ccee6542efb74e2b07adb65479cc5",
            "7162726e3b3a7f9c515749a18723ee4439dadd26c0d60e07dea0f2267c6f40a7",
            "10e62bc629872c7d91c2f2edb9643b7455e7238a8c9b4074f1c5312ef162ba22",
            "4336ff82b3d2d7b9081fec5adec2943329531c605b657c11b35231c13a0b8571",
            "173ec57a878e2b5b0922e34be6acac108372f34dace9871a894fe15ed8",
            "7f73526b1a9379be41301cfb74c55270186fbaca63df6949ce3d626e79304d92",
            "7d91aee3b51f3f8d92df52575e5755d97977dcdfb38e74488c613411829e32",
            "8e42e588e1d09ce03c79463e94a7664304f688caf4c617dbcbca64a635bbe79",
            "8f1f9b3919c879f4ec251871c19b20725bc76d657762b5ddfdf3a5ff4f82cb47",
            "989bc66d66ff0513507bcb7aa27f4e7e2af24337c4e7c4786c4630839966fdf7",
            "bdfc818d40b635bcd3d2e3d3b977651d0da0eea87f13847b99dc7bea3338b",
            "5841684ec6a1f8ba919046857dac9175514fef18a2c9395dc3e341b9f5e778ac",
            "211e121448c83125c945cd238dc4f4c5e9a320df5ee3c8c9ad3bb80c055db876",
            "3cce5c4d27979e98afea0d43acca8ebddc7e74a4e62480486e62ee3512",
            "68d98f9a56d4c0ab805c6805756171f4a2cdbf5fa8ce052a4bf4f86411fb080",
            "e2629467cf544e79ed148d3d3842876f2d8841898b4b2e6f60dfc1e02f1179f3",
            "6162abdfb5c7ace7d2caaabdc5d4fdfc32fb63f2a56db69f276167dffce41",
            "af336a55d17916836ce0ed102cbdb0fa6376544971301e0f28beb3899c649ff2",
            "1580729b12e1d6357e7eaa088fbf06ba2c8a2fb35b0ba4c5451937d3f134ca9",
            "1f72e604cdb4c49f7106b594ac87eff3ed6a1999255437241f57d28c45d103f",
            "542a699fe314b9f747eed89b9cae23fdefc27684f6c13dc4a29f5d057cc12d",
            "b2a4cd136505395a876113a800aa8ec15761ca3d945b57e0d0dcdcfeafd7a6d9",
            "907fcce8c3a8d337a6aff226847e3cc7c9bb6bd02f43be1b7c71b3dcd244e11",
            "62a6c3e6b8cbd4fbcb5866289642bb5b5a90dd16e2c28dc054c9d248943834f9",
            "173481eb7f2157a5ad79ec765d36be5736395b72ee519185e23f436ba15185",
            "9ad4ffb57819e7ff633f60b5901e44a3570573ad4d453075b72ae2cbd23c8a6d",
            "8c064476ed9de9ca437cf869127c61a945ea6c308e9b25e4a991bb252c6d754d",
            "9ddd647a59a93c23ce49cece35f7529985ee40d0ca7ead6a1e3fe0f97b286162",
            "c56ab25347aa70f406a85d221da104c5ff05d2a1866a1b57dc1ab4f5feb97",
            "7dcb1d264010bfac568d19e9baee3c2a2aaa729d6cf335b9cf62d2fb2f4c813",
            "fd22ca2c137a6ecf6a4366eb1f2c8a6b173220b295abc1ae13cedf93dabdbf3c",
            "7245bc1d62123b6f8c954cf08be76c9c0d23e778ca9843935a24782c8b2bab",
            "721d9bc16854e75bad69fc7529e3f4c82f32a4715f219697f413c67115a93",
            "e1aa418bd0b4f4d37d6853b7c577eac34034d2f64b6415ff653132f4ca66cd7",
            "c2159fdfe7ef9f12269e2791ebc5aca8e787506b28bfc69747ccf12671261afb",
            "8362ff7077a326747210c56031dc46a141f25454b27873395eda6483d55df",
            "6845756829b6bca516b5bf9251ae31c79cd6ddbc3c57f119370b0ccd8d6f5a1",
            "b77b40e51c7562e523efb0c0f94a616da97c1f485fd8b4a4ac9fb37561812",
            "48b34cc77a18dfa0ebb54f93c3c31779769f519f41b5153c1869aedec9965b2",
            "44d65b5b742333fa051b81b8365155618d4231becd9393283ec639b2f12b7f93",
            "dff36d1281f862f8841d2b84ce17c560e45cd0b3fd879c78c13d26b7c7f7cbfe",
            "1cdeb260f0b31796aca16be0c79ea0169b6f6542fef743c09c4238ad2114a49f",
            "1ab96446ecc368e4c685239fb6d14f7adbf337fb343da95285eec68dd79c4a",
            "7e9437c77b2529da8dbb0545f2898e9a2d12e667f8e6f69f051ced32acfde",
            "b5f0d648162c98c6ee9cb31c4e5cead456dd105a37f7ce8a7a09d384a47b8b2",
            "7f9712869fb1ffbd4905342aca6b7a4f5e47ee9ea7dae5e752c7b9e9bbcea",
            "5485ef7d262e19a65756ec94338777f93b16f64da2783189d0ee34b816b357",
            "869b276cbe44c4a5918beb106e625ae36f829e7c7bcdfad8b67565f48430b199",
            "243ad02c2f5bf1a4b8225a1f6243d507e707fda68237f2aa738467c956be10",
            "dd80f354e47a8b66bddb43ff38d972487aa1105d1eebdb7a26844c140d888e0",
            "32aba3b2955a782f0a3a37bfa9c4173919bdc4827c99bee5670169ec4e181dc",
            "c9939c9d2d9e5e5fb689b12d89e9edf08c915866b7661545fe46e88ab1551",
            "c61aec3d73110435b3c549f7bb70d4aa6d6e6c404590b34be63e6ab42c2946",
            "c96522d14a7a21f59fc2c66ef82fbe62263c9d7d064f823b7c1a614e409099",
            "5a75720a749dca3fee845d6d7e9b2234542f1a2d7d948f040c5ca3e493f5e4",
            "4a213a331b92693ec8f534f627803ac8492df0916b70a762e28aff6a5d8ea2",
            "c149f0fe3a696fad6fd6ad7858ff788b6d15129207b5f72b0d7d7f983e1b",
            "6c175165d75062a323c5865916162ce7eca5e6ac224440c8b0536c96530d33a",
            "acf665de64faf18cbfd1d13598fd4552566c878fbc3716f52587e2cbae44b9",
            "ff167fcbb98ecd6377905add5c15459e3fb815a0b7c9142e8037a818945630e",
            "3e8a56d37decce24f73e3e97e67812a2f5a1384a525f4c0e58cd3fdeffc38",
            "4d6e309a40b631ec6cb5be74e22b883da452f43acf4e834f43c1cb25c8f82a",
            "7417341979bb41714df892d4994d63347006be8bd7f2cfb65b826d2b21172",
            "fe74c2edd110608e523ce6b6b31f528cb38a122941ede2d68d61ba52bd6802e",
            "04426382d98452d90ef2cdb492af67853ca8542972595351acc6cbf88f51532",
            "c35dd24dc529b664504bfd3315b3fe8ca9c6c9b9fe1e84ce6aea216ef7bd3",
            "d2498d91a41fdb77f58c9da73073cc3f287b93ee12ccbfa6473d55019454b1d",
            "3fca4000b6c9b5c7d57f29245bb9ee00af282e351d697a44031d15a1384eb3e8",
            "f3e399b37b4fba7d2fd0ed14f8a6820131d6c9a355282704c59796f092677542",
            "1561ee2ea67346c667b4e96d85847b7b51372b605fe34ae046c8c0d0d2973d",
            "40f836d124597ab614e97ab9bae81bcfacfb9a5cb87b8ce9fc50e5ab93c53dae",
            "52ff1cf6537438f4aff7c2cdcdc84cca8f42f9aa264913827981ace5876f71",
            "8983c344fe3bb69d16caa51766a5bf371ec9075496a061334db9f9a44711",
            "76acbc4d98a2deab2b2b8e7798d5b9ae54e1d5710c9b0e93c243461405d4519",
            "b4c815e8e24ecc26ce18a35a938a5d8b6f96e9c8467841be37699d83e43d",
            "16b5b6a89aa283548411eef311b2ab46216a7b0538452b249386895b917cf2",
            "5ff1fd2453f6d85961de8976bce16c5d514e38ad7f846e99317ca4a3b5889",
            "1b144c7532ec233ffb5c52e69e3d98be12d52481fd5113703f21e1ec850b6",
            "64316ffd3ede6158b3eb421b97cbf9b82dd93e08d54b3f9f0105c75f134f89c",
            "b6966498a988780cd1dcf4ea059eeec6497fe14b95c32470bc6d99b17ff1",
            "b9ec71e4727fde6edef0d08f25561c4ba6eb21583fd9fae443e566c79d88e160",
            "3385c509e1b649552c750dc23c344d24d158df94f6d8532377a9b726462bc05b",
            "59f87bef785eddc72f2289b8482375266bc3ceed365c270c5ef7f835df39",
            "27d4d629ac756da03d894556535bcca033fdaf172e69cc772262b43918ede351",
            "af43feeb32559878e0561c87f8f35c9812973bf27661d874bf68bb569b333f45",
            "ef1f3805eb46853b22559404b373c54b12453c4882abf3dd7673f5869be4cd",
            "7265757c8a5a826f9e2b68e4631fee33e74dcbfcd9e4c744360186f4ff58fa2",
            "3bf9314d6f78711c93d895519bc620a8176819551dc1d498aea840f32cf0d917",
            "ae97b72b9972d5db2516ceda54c6837116c2c52e75763749de9949aaab95d0",
            "6512d4661323db375b829bf2e090f7c3a277f95d3a5613ae59a06d9a9a270",
            "bf948ef3d865729d4120179087c0323a4cb913119932aa620dd9accef7d528a",
            "bb9688ec3a8fb8f18887377bd5be94a56fafb267d870c0532c356cc35adc",
            "4097a9b1113fa753d37d613ab9e118f0d05d3f5276f965f6466bb25d313a0a9",
            "40952ce63957766d68819e9e033429db2f9a472b3646d856e8b839088de699f",
            "59c13c5c833d4205fb899fe6a329f136c5c67ce0dd86efb834684686ead2d",
            "762b16ce467a4096e188f9c12351e66fce8ef1e18b6e9788befe4666c68876",
            "80516a7b5faf2cc796650c51c167773ba8c8e73e94b10d96d3e9d827dd63c5",
            "5a956dc2631e54a3cb62d31390226b5cc052432fc7b9261da4bf6420f8d7e8",
            "156c183d1064d9d72e25de3945ef16483c15eb06b6c87396ab4ba1f8e9b6df",
            "1d387e3e5b89925ce6519cfb4378af11abed6e4b7ae3491f93048971a2e80e7",
            "8fcdcc72bfd5192d752d1a5eac7c11115c9aa43d574dfa85f99f2896c9b15",
            "b6557aa1aaf5fe97745da389ab69473ef9f5ec31960be2860a5c8bd6ed37",
            "a191322292ab595ff53c704b85f514f5b9f45470332ac2719eb85e92df4023",
            "93e15c711b3a37d5634a1b629cb9e43f793f297dd3369c2bdd9b4bba80fa6b",
            "489fd1a12c42e0fed383e9d23bacb95815fb752213849ddaa8b5893ac7eba24",
            "fbd1fa49884dacaed4cc4650d23bfea4dc7a89dce8d90a2e27acfb712e8f8",
            "daa35aa45c2d7092e359962e79d11842ed18ce499aacff22c5871662f7a69dd",
            "447374bbeadeaa36684d3f68eb46bed5b7d145a206d5a54b9c12382d6b1f9dce",
            "9c8f4d6466382820536e82842a162615c2e7d2316afc59264a9c3ede",
            "997eb6a7b37bc8924ed341a4a0a356112b620bbc121b5ce27e692a535d2df81",
            "adc9c2fd56f6698f5807012e4dd2e785e5efe1e6799b47cd1c3bdb1c05eda3c",
            "2d13cdd15b5673a27a63c04226e3b2b3639ac27fb853d1a146a239496da1ff",
            "238580ddf446509b4c84e829b39a8b2f72ab8cd649dca6886405dd2ad2dcd5",
            "241c4fdecb52afd81b24993b5a7e6c715f375d94f4eafab39a60bb2b5050e9",
            "d2447fe1ee25c2476525e78aa71bd2f56bdec3b5f829215650642563698d272",
            "f515cc3ace7a74afe76e41c117dace9f278b63c1829d30b246bd7d73584cf2",
            "9691b4eada776b725eb2a5fdda77af65b87ccfde6ccb76c75fbe4da347723",
            "344f6ae0fa81aadf8a2197e656cb8e696d18f12e2a87ab42c41b64b61c688",
            "6b76f913d7c02718c7cf9a8087db3dc246d9eb89febefd28abfd59226559548",
            "eed8565e62768584c3a933227e1747165dd86e3942a93f52e4285ba65cd2662",
            "8fee25beac53be9a196b5319e9887eaff50cb19a61ad5d88bb57fe431c8a8",
            "e32379103e70b548a716a1c8d477b611c44c6d1925e978f1164362f22564b9",
            "c8424766a87919285dd9f92e4c8868de558d87c739d76668b532f7c21a490",
            "ad369862824c34b9354edc9cb14832364dc74cf867863210c1a83dc3ebcd22c3",
            "a17d731aef41184853bafa292fa9f46c6c56912cdd1bb4cd43e53c88ba82cc6",
            "ff642bf35a3e622515bb1f20356534fc3f24316b3eb170f477f336ec26edf9d6",
            "dd222e15fa6d522d9bc3a674d1a9327bbee733b396246e40af1fce716bef9b",
            "7fd6bf7bc58c661dbef8b8896eb2ccd31229a3e8b09e54d769c1e46242348277",
            "4e5223efd125cba238eea12334f9e856718842281d7f865db3c6d577ed5e084",
            "689026f3bf84461b773a3e7915da121dde4a3371598a8431cd5e8951ea549",
            "80c86c1d62b71928d6251ef238434e226fa9fe3041964625d8a0e550d8f528",
            "d275c5282d3ce248ecec75b82a44c2e70497c76565c993618316ca12a1efb8a",
            "4c232f62387b2ac54c371beaaa0577fe7778a3c6954b34914c3e016b84f6f46",
            "2ecf8d5923446aba9eeef5a24848b84b20bbc37c80ca62e9e664375c24dc7077",
            "d5fab8a6fc9ec343f7ccecd86a0f5cc339d11a02c8fb0ba1ed5cb446d01ae0",
            "ddeb44d8f85ee1b918dcd231eb62887e2e2df63df8c91d11461111f710c9f2ac",
            "c25c40b36da47d6a982c86a319d6fe5e4916df411fbbf656451bd6049d6d179",
            "5fe8b3813cf0c55cdf2c97789735d62085767323cd9af8da804669592ac45f",
            "7cbe75103d02c10dd410fc5760139212fbeda8d91ee752e53e4674aecfa30",
            "35873a599e65596b99080d86f5b5ecfe162a8235a136bb4990fb8e2325965b",
            "f813e90a33dce8bca6a6c688683706498e1f2aaec8530c481c2a80faa82ddebc",
            "99a5daa3fe44c414ca8f4324c36bced2afe6d962a580842d8a36c5d9c1b8be0",
            "672d94becd9f61dec864a8232692fb1c54ff4676ade457ae6847f7d9d954a8d",
            "b0a74cd03493521b451cbf256775e93809a203672e837e0eb46c590e5f0928f",
            "f3a01fd5a6267170ff7c6526d3a9ee4ee4403e036955f902c9414c064d9449be",
            "bfd4e3b0527bd94824e530968f2281ce8e3dd9a3f6b73fb23e6cc15b7c79d2c",
            "4fda29ceab6ca457da30882493c5129287277895ebd3f244456126666a6",
            "79b7861ac71a511be1b88823549fbbbbc8a300dccc0e873322ea2a7859d686cc",
            "3debc1619533fe5f011783e43e526efad44bf49ace9e5ca10badd7f55e069",
            "dbc1c832b4919315df977aedc7ece84d9aabef8823cc9de5ceee5129b1728",
            "7a27c712e9446026aa38afd114d76cef0b96bdbfe58adbf73a9a9149684eac3",
            "b78e4089143163c32291d6365e715e1b42806a40784ff93b8737947c7687e9",
            "3a3d66c09223d0899b896487505fda388584941ec946534d2a9e277133fe02e",
            "1ae5861b10c43426dc4345aa49585d818886a1fa4599c8ada0b2fcbf69b81",
            "a7924326aca6415f34b017573996f333ad1c8db9bf46d4fa216493faafbc9fd",
            "62cc348490c1d2bf32daed40eae64bf812c3ba23abda3653a1335af6f2123b",
            "c9b2b946ab9aebc76084157641aceed34e83185d958fc2f225c2595c8171d6",
            "81895e92fc1552fe1dcd531f6775e1266146b75812c58795a1b0a3bef922b79f",
            "8569245371edff63a26913a972f2c44fbe41f75e42668a72599759cf452f3a",
            "2470257f2b1ea354f4a1c5e0e1ee207d162f669a83e440a913cd87f58f52ffd9",
            "1670d1f3de92402e51e780b2e6699dabbc79fb44dca1c273f5cbde64258aa",
            "dc3938ebca030fe937c77b9876d456e19e9b6588f3ee8ec993bace98ccae4e",
            "1ad471136cdf5e9dec7b30c841f2b9af1aab09b9f369fa24aa52a98f4b0afe1",
            "c828ea469bef8c84eee8e9abf66788c7991789d8f3a21ab460789a0b14884",
            "16c63d6591c8e7e3a1b8825e43bb8dc8ac561ab567a9cda57cf783031e0",
            "62604b8b7df3da89a0a85ed8152073da59f31774cdcfec66951edd572a72957",
            "4b28dc744eb31957b74a14bcfc2320b451f5b6859a158fd8bab28dd873f71b",
            "3d4a203011d23899e4f02065324b4c9e97228f2ab2cef1eb366c8edbaad26d",
            "b6ed84678adf2445348963fc343a6f44e8631a1d7fd25f377d824e9bde37e",
            "34f889c53b26a7b68569a51be9a5d17d3fca371df6dc615183e2b437530",
            "b7d33aa3cf94603b6bbaccdda885c33bdba3baf6f866b9c1dc64816cf47e7fa",
            "bd2730d152b782f5e6f26e5d22f49d3da81ada8cb193fecd37189a72b9a7c",
            "49e3bd98686d1146b9337c9652899a12f28fe59e42f123578e8981652510e5",
            "4c4d84cc6798da26511812353857e7630544ecbb93a7d9a5a44e0a8bddf3",
            "de915d709d818429c09838c6341ce6c4abf41e450def2dbb4c12adee5745fb",
            "f1d8494d382feb5e7434daa2fe553b4d6b51269717cd12309d331399efdbbd49",
            "c41043fb371ce1d6bc2b5e3c38c116177a71aba9c2e9511188f9e15955131",
            "a5e77eca3a8571b6ea72a2d1d41429323c610d35477191d5191faa9745d772",
            "f42d8249035be11d50ef8c2a947d1ffe3eb8d91989de5b27748190eb231295",
            "2e24edaf3df936127c1be1097bc798411719489a1652139ebd6f8dd21fda71b",
            "14ea486fef4c37c771f23968df9d47358111d3cac57c458c57b6a2c7dc9970",
            "7dc27a418be63cd1ece8d2daf106c5b369db846c6c141d2bde4d81c83eff",
            "f911f9329b391d8b6e30d55ab9f20608d620d682c9327514b5ab9d1b7ed360d3",
            "c0554e6189ce7ba79de273dcac358f2985771d3e3cec7ff8b4c953bf6d5c5",
            "f60648e541171fb69121794cc8894c3168ba841c897cbb457819172d9df3",
            "7c5aeae0e15227404b1f1c59c93e9ffce83c7433feb5649ecc2ddc1af6e2c7",
            "9960335e7981e3c82b03a59bce8aad04b893efc9928e825498199d59627079",
            "579a5713ed8affbfe8bbcd432def758ec4a31647db4f7dda4df7535a3fa0f58f",
            "7fb832fa27791731e34a1adaf6f59c1a95e6e5aa46d528f6c2975d668bc1",
            "cae2c0eb1730e11888e3f4dc133e9c5fd1434beb19b1616b026412fafc8e87",
            "7ca423e35c767d5844f8bb9a3c949710e8615847e3f6db117591ce53c30f9e",
            "cf9de3c33c4b523248fc7dc23f18c551f1d2740cfeb162674aad031852e",
            "f4254838c33ea227ffca223dddaabfe0b0215f70da649e944477f44370ca6952",
            "fd41e45153cb159af3d2b3d0e4210969fc4a6402a327c5ab6d1f6981ceae7929",
            "6437c4b8eba97a3f79a49074d8c9c6c7ededabffe2896161256d426014f52dfc",
            "8b1ce430410e2416c1c0ae1ad2b91621e128b9c8a46e32dfee9a6e777eab6cf8",
            "1e1408a4152663ccd1169965eabd5815216913bf9f9374d499ed3160a562cef9",
            "e814f4ac1be28dd2ee5d6d297265d2efac52c36035a60bd919961914ea226ef",
            "8958ea6b50dbb139ec56c20fa3d61d4902c30d4ee234d8009180bd9c37f33708",
            "a7556d263c98a1d3b12236b56f613c516d91c755d4d7555eff0788da9c134f2f",
            "6e0b2d4281ad9a090037ef0985fc0667e43c4762c083d34daaa6cd05d5e53055",
            "7ae4fce4ecdfcf9e27e8723061fe43236ec937004cd0e016c7fd924587a34c5d",
            "adcebc4e9a583d84ee7083b5540b8de8499f9ed4e02619213219c2c6eb081821",
            "c6e4f799a1019320407a035ee54350b3a507523c7d5625f13b372a7b215402c9",
            "23c7a66adf72ebe7338b23a34e5bf02827433c805e8b63c2a19ed9617df26b2f",
            "81280cc515d765b001c9f628a4f488b1a910effd38bc24e0b5aa64252e68068",
            "2dc567c2c3ee555d22944a5d9238c3d27915f5076f5ced1687471afd0dc605d4",
            "fb44f9b19f45f1356f9c5c85ff2137018068e50c84ff0b2ae7d9ce0f33629d34",
            "52b273741996ce877d63a8651e94ba3c55fee196c34fd52b78c0aa06c5fd550",
            "48cc9961ec54f340f0d35239562d92748e92c6e2d6aa7aac884c414ae949618e",
            "a8485f852d273ab6223517c0d84c4c13f704ea5e40a7431c5012bb2e86250445",
            "8fba7e9fa894246022930de1320368d013544d6953f192c62561c1423bb44dd5",
            "3ef953f0f2d9bdc2d2156952d7aae3fb438216a47108cbf0a0348f26e018049b",
            "2ab3f698e25c02439c7b2dbd7b6648e2ecb3ddcc34e638ed4d116b0b409aeb25",
            "2585c58e856754f3d7e36f427caa95aab8ff19781a1ba645acb428e86452b5a0",
            "99e3b82da73910648899e454691e68f8d15f276dc9aab1435feff8a047948a40",
            "75867d2c0a93cc99e05a86d55637e7f47fef7c9f98a9e5158dc36dd1b414bd90",
            "b283088987c25b6153bbc1261864bc1764985dc4dd94bd5ebe040f598f7c4d59",
            "5b71b9a4ff7292f7c4955407a97acc93d2784620a53367117713bfa07d0909c2",
            "b1732d69af0612111e8f15ccc105013fe2ce08c4c65b65833b6456e4f01238ef",
            "519d503b6cd7565119361cf6b51ef8d294a951b4b512d2727f28b5cc9d784626",
            "d2da19710b8a4171ac9b17984dd95d042d92742d72a74ba40450d7494a24321",
            "1d9e8dafe7d87bb7cba7eb3d8d2d5bf58eab72ecdfdf9ecce3d1c03871c0"
    );
    private static final List<KillEntry> KILL_ENTRIES = new ArrayList<>();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final ThreadPoolExecutor WORKERS = new ThreadPoolExecutor(
            2, 2, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(128),
            daemonThreadFactory(), new ThreadPoolExecutor.AbortPolicy());
    private static final ConcurrentHashMap<String, ProfileRequest> PROFILE_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, SuperstarRequest> SUPERSTAR_CACHE = new ConcurrentHashMap<>();

    static {
        register("default", "Default", "[\\w+]{1,16} was killed by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was knocked into the void by ([\\w+]{1,16})\\.", ".+ Bed was destroyed by ([\\w+]{1,16})!");
        register("fire", "Fire", "[\\w+]{1,16} was struck down by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was turned to dust by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was melted by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was turned to ash by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was fried by ([\\w+]{1,16})'s Golem\\.", ".+ Bed was incinerated by ([\\w+]{1,16})!");
        register("western", "Western", "[\\w+]{1,16} was filled full of lead by ([\\w+]{1,16})\\.", "[\\w+]{1,16} met their end by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was killed with dynamite by ([\\w+]{1,16})\\.", "[\\w+]{1,16} lost a drinking contest with ([\\w+]{1,16})\\.", "[\\w+]{1,16} lost the draw to ([\\w+]{1,16})'s Golem\\.", ".+ Bed was iced by ([\\w+]{1,16})!");
        register("honourable", "Honourable", "[\\w+]{1,16} died in close combat to ([\\w+]{1,16})\\.", "[\\w+]{1,16} fought to the edge with ([\\w+]{1,16})\\.", "[\\w+]{1,16} fell to the great marksmanship of ([\\w+]{1,16})\\.", "[\\w+]{1,16} stumbled off a ledge with help by ([\\w+]{1,16})\\.", "[\\w+]{1,16} tangoed with ([\\w+]{1,16})'s Golem\\.", ".+ Bed had to raise the white flag to ([\\w+]{1,16})!");
        register("multiverse", "Multiverse", "[\\w+]{1,16} was distorted by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was thrown into the singularity by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was shot into another dimension by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was thrown into a black hole by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was launched into a wormhole by ([\\w+]{1,16})'s Golem\\.", ".+ Bed was sucked into a black hole by ([\\w+]{1,16})\\.");
        register("limbo", "Limbo", "[\\w+]{1,16} was sent to limbo by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was pushed into limbo by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was shot into limbo by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was launched into limbo by ([\\w+]{1,16})'s Golem\\.", ".+ Bed was sent to limbo by ([\\w+]{1,16})\\.");
        register("love", "Love", "[\\w+]{1,16} was given the cold shoulder by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was hit off by a love bomb from ([\\w+]{1,16})\\.", "[\\w+]{1,16} was struck with Cupid's arrow by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was out of the league of ([\\w+]{1,16})\\.", "[\\w+]{1,16} was no match for ([\\w+]{1,16})'s Golem\\.", ".+ Bed was dismantled by ([\\w+]{1,16})!");
        register("bbq", "BBQ", "[\\w+]{1,16} was glazed in BBQ sauce by ([\\w+]{1,16})\\.", "[\\w+]{1,16} slipped in BBQ sauce off the edge spilled by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was thrown chili powder at by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was not spicy enough for ([\\w+]{1,16})\\.", "[\\w+]{1,16} was sliced up by ([\\w+]{1,16})'s Golem\\.", ".+ Bed was deep fried by ([\\w+]{1,16})!");
        register("woof_woof", "Woof Woof", "[\\w+]{1,16} was bitten by ([\\w+]{1,16})\\.", "[\\w+]{1,16} howled into the void for ([\\w+]{1,16})\\.", "[\\w+]{1,16} caught the ball thrown by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was distracted by a puppy placed by ([\\w+]{1,16})\\.", "[\\w+]{1,16} played too rough with ([\\w+]{1,16})'s Golem\\.", ".+ Bed was ripped apart by ([\\w+]{1,16})!");
        register("santas_workshop", "Santa's Workshop", "[\\w+]{1,16} was wrapped into a gift by ([\\w+]{1,16})\\.", "[\\w+]{1,16} hit the hard-wood floor because of ([\\w+]{1,16})\\.", "[\\w+]{1,16} was put on the naughty list by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was pushed down a slope by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was turned to gingerbread by ([\\w+]{1,16})'s Golem\\.", ".+ Bed was traded in for milk and cookies by ([\\w+]{1,16})!");
        register("primal", "Primal", "[\\w+]{1,16} was hunted down by ([\\w+]{1,16})\\.", "[\\w+]{1,16} stumbled on a trap set by ([\\w+]{1,16})\\.", "[\\w+]{1,16} got skewered by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was thrown into a volcano by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was mauled by ([\\w+]{1,16})'s Golem\\.", ".+ Bed was sacrificed by ([\\w+]{1,16})!");
        register("oink", "Oink", "[\\w+]{1,16} was oinked by ([\\w+]{1,16})\\.", "[\\w+]{1,16} slipped into void for ([\\w+]{1,16})\\.", "[\\w+]{1,16} got attacked by a carrot from ([\\w+]{1,16})\\.", "[\\w+]{1,16} was distracted by a piglet from ([\\w+]{1,16})\\.", "[\\w+]{1,16} was oinked by ([\\w+]{1,16})'s Golem\\.", ".+ Bed was gulped by ([\\w+]{1,16})!");
        register("squeak", "Squeak", "[\\w+]{1,16} was chewed up by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was scared into the void by ([\\w+]{1,16})\\.", "[\\w+]{1,16} stepped in a mouse trap placed by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was distracted by a rat dragging pizza from ([\\w+]{1,16})\\.", "[\\w+]{1,16} squeaked around with ([\\w+]{1,16})'s Golem\\.", ".+ Bed was squeaked apart by ([\\w+]{1,16})!");
        register("buzz", "Buzz", "[\\w+]{1,16} was buzzed to death by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was bzzz'd into the void by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was startled by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was stung off the edge by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was bee'd by ([\\w+]{1,16})'s Golem\\.", ".+ Bed was stung by ([\\w+]{1,16})!");
        register("oxd", "Ox'd", "[\\w+]{1,16} was trampled by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was back kicked into the void by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was impaled from a distance by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was headbutted off a cliff by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was trampled by ([\\w+]{1,16})'s Golem\\.", ".+ Bed was impaled by ([\\w+]{1,16})!");
        register("pirate", "Pirate", "[\\w+]{1,16} be sent to Davy Jones' locker by ([\\w+]{1,16})\\.", "[\\w+]{1,16} be cannonballed to death by ([\\w+]{1,16})\\.", "[\\w+]{1,16} be shot and killed by ([\\w+]{1,16})\\.", "[\\w+]{1,16} be killed with magic by ([\\w+]{1,16})\\.", "[\\w+]{1,16} be killed with metal by ([\\w+]{1,16})'s Golem\\.", ".+ Bed be shot with cannon by ([\\w+]{1,16})!");
        register("literally_spooky", "Literally Spooky", "[\\w+]{1,16} was spooked by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was spooked off the map by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was remotely spooked by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was totally spooked by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was spooked by ([\\w+]{1,16})'s Golem\\.", ".+ Bed was spooked by ([\\w+]{1,16})!");
        register("memed", "Memed", "[\\w+]{1,16} got rekt by ([\\w+]{1,16})\\.", "[\\w+]{1,16} took the L to ([\\w+]{1,16})\\.", "[\\w+]{1,16} got smacked by ([\\w+]{1,16})\\.", "[\\w+]{1,16} got roasted by ([\\w+]{1,16})\\.", "[\\w+]{1,16} got bamboozled by ([\\w+]{1,16})'s Golem\\.", ".+ Bed got memed by ([\\w+]{1,16})!");
        register("dramatic", "Dramatic", "[\\w+]{1,16} was tragically backstabbed by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was heartlessly let go by ([\\w+]{1,16})\\.", "[\\w+]{1,16}'s heart was pierced by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was delivered into nothingness by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was dismembered by ([\\w+]{1,16})'s Golem\\.", ".+ Bed was dreadfully corrupted by ([\\w+]{1,16})!");
        register("noble", "Noble", "[\\w+]{1,16} was crushed by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was dominated by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was assassinated by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was thrown off their high horse by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was degraded by ([\\w+]{1,16})'s Golem\\.");
        register("snow_storm", "Snow Storm", "[\\w+]{1,16} was locked outside during a snow storm by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was pushed into a snowbank by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was hit with a snowball from ([\\w+]{1,16})\\.", "[\\w+]{1,16} was shoved down an icy slope by ([\\w+]{1,16})\\.", "[\\w+]{1,16} got snowed in by ([\\w+]{1,16})'s Golem\\.", ".+ Bed was made into a snowman by ([\\w+]{1,16})!");
        register("eggy", "Eggy", "[\\w+]{1,16} was painted pretty by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was deviled into the void by ([\\w+]{1,16})\\.", "[\\w+]{1,16} slipped into a pan placed by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was flipped off the edge by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was made sunny side up by ([\\w+]{1,16})'s Golem\\.", ".+ Bed was scrambled by ([\\w+]{1,16})!");
        register("celebratory", "Celebratory", "[\\w+]{1,16} was whacked with a party balloon by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was popped into the void by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was shot with a roman candle by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was launched like a firework by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was lit up by ([\\w+]{1,16})'s Golem\\.", ".+ Bed exploded from a firework by ([\\w+]{1,16})!");
        register("wrapped_up", "Wrapped Up", "[\\w+]{1,16} was wrapped up by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was tied into a bow by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was glued up by ([\\w+]{1,16})\\.", "[\\w+]{1,16} tripped over a present placed by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was taped together by ([\\w+]{1,16})'s Golem\\.", ".+ Bed was stuffed with tissue paper by ([\\w+]{1,16})!");
        register("to_the_moon", "To The Moon", "[\\w+]{1,16} was crushed into moon dust by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was sent the wrong way by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was hit by an asteroid from ([\\w+]{1,16})\\.", "[\\w+]{1,16} was blasted to the moon by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was blown up by ([\\w+]{1,16})'s Golem\\.", ".+ Bed was blasted to dust by ([\\w+]{1,16})!");
        register("festive", "Festive", "[\\w+]{1,16} was smothered in holiday cheer by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was banished into the ether by ([\\w+]{1,16})'s holiday spirit\\.", "[\\w+]{1,16} was sniped by a missile of festivity by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was pushed by ([\\w+]{1,16})'s holiday spirit\\.", "[\\w+]{1,16} was sung holiday tunes to by ([\\w+]{1,16})'s Golem\\.", ".+ Bed was melted by ([\\w+]{1,16})'s holiday spirit!");
        register("roar", "Roar", "[\\w+]{1,16} was ripped to shreds by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was charged by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was pounced on by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was ripped and thrown by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was ripped to shreds by ([\\w+]{1,16})'s Golem\\.", ".+ Bed was ripped to shreds by ([\\w+]{1,16})\\.");
        register("triumph", "Triumph", "[\\w+]{1,16} was bested by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was knocked into the void by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was shot by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was knocked off an edge by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was bested by ([\\w+]{1,16})'s Golem\\.");
        register("stat", "Stat", "[\\w+]{1,16} was ([\\w+]{1,16})'s final #\\d+\\.", ".+ Bed was bed #\\d+ destroyed by ([\\w+]{1,16})!");
        register("bridging_for_dummies", "Bridging For Dummies", "[\\w+]{1,16} had a small brain moment while fighting ([\\w+]{1,16})\\.", "[\\w+]{1,16} was not able to block clutch against ([\\w+]{1,16})\\.", "[\\w+]{1,16} got 360 no-scoped by ([\\w+]{1,16})\\.", "[\\w+]{1,16} forgot how many blocks they had left while fighting ([\\w+]{1,16})\\.", "[\\w+]{1,16} got absolutely destroyed by ([\\w+]{1,16})'s Golem\\.", ".+ Bed has left the game after seeing ([\\w+]{1,16})!");
        register("social_distance", "Social Distance", "[\\w+]{1,16} was too shy to meet ([\\w+]{1,16})\\.", "[\\w+]{1,16} didn't distance themselves properly from ([\\w+]{1,16})\\.", "[\\w+]{1,16} was coughed at by ([\\w+]{1,16})\\.", "[\\w+]{1,16} tripped while trying to run away from ([\\w+]{1,16})\\.", "[\\w+]{1,16} got too close to ([\\w+]{1,16})'s Golem\\.", ".+ Bed was contaminated by ([\\w+]{1,16})!");
        register("old_man", "Old Man", "[\\w+]{1,16} was yelled at by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was thrown off the lawn by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was accidentally spit on by ([\\w+]{1,16})\\.", "[\\w+]{1,16} slipped on the fake teeth of ([\\w+]{1,16})\\.", "[\\w+]{1,16} was chased away by ([\\w+]{1,16})'s Golem\\.", ".+ Bed was sold in a garage sale by ([\\w+]{1,16})!");
        register("glorious", "Glorious", "[\\w+]{1,16} was stomped by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was thrown down a pit by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was thrown to the ground by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was outclassed by ([\\w+]{1,16})'s Golem\\.");
        register("lucid", "Lucid", "[\\w+]{1,16} was put to bed by ([\\w+]{1,16})\\.", "[\\w+]{1,16} fell asleep and was then knocked into the void by ([\\w+]{1,16})\\.", "[\\w+]{1,16} was knocked unconscious by ([\\w+]{1,16})'s arrow\\.", "[\\w+]{1,16} wishes they hit the hay instead of the ground after being pushed by ([\\w+]{1,16})\\.", "[\\w+]{1,16} fell asleep to ([\\w+]{1,16})'s Golem\\.", ".+ Bed was destroyed by a half-awake ([\\w+]{1,16})\\.");
    }

    private LegacyDenickSupport() {}

    static String skinProfileName(PlayerInfo info) {
        if (info == null || info.getProfile() == null) return null;
        try {
            GameProfile profile = info.getProfile();
            var textures = profile.properties().get("textures");
            if (textures.isEmpty()) return null;
            String encoded = textures.iterator().next().value();
            JsonElement decoded = JsonParser.parseString(new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8));
            if (!decoded.isJsonObject()) return null;
            JsonObject root = decoded.getAsJsonObject();
            JsonObject textureRoot = object(root, "textures");
            JsonObject skin = object(textureRoot, "SKIN");
            String url = text(skin, "url");
            if (url == null) return null;
            URI skinUri = URI.create(url);
            if (!"textures.minecraft.net".equalsIgnoreCase(skinUri.getHost())) return null;
            String path = skinUri.getPath();
            if (path == null || !path.startsWith("/texture/")) return null;
            String hash = path.substring("/texture/".length());
            if (hash.contains("/") || NICK_SKIN_HASHES.contains(hash)) return null;
            String profileName = text(root, "profileName");
            return validName(profileName) ? profileName : null;
        } catch (RuntimeException failure) {
            LOGGER.debug("Legacy skin denick profile could not be read: {}", failure.toString());
            return null;
        }
    }

    static CompletableFuture<ResolvedProfile> profileByName(String name) {
        if (!validName(name)) return CompletableFuture.completedFuture(null);
        String normalized = name.toLowerCase(Locale.ROOT);
        URI uri = URI.create("https://api.mojang.com/users/profiles/minecraft/"
                + URLEncoder.encode(name, StandardCharsets.UTF_8));
        return profileRequest("name:" + normalized, uri, LegacyDenickSupport::parseProfile)
                .handle((profile, failure) -> profile)
                .thenCompose(profile -> profile == null ? profileByPlayerDb(name) : CompletableFuture.completedFuture(profile));
    }

    private static CompletableFuture<ResolvedProfile> profileByPlayerDb(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        URI uri = URI.create("https://playerdb.co/api/player/minecraft/" + URLEncoder.encode(name, StandardCharsets.UTF_8));
        return profileRequest("playerdb:" + normalized, uri, LegacyDenickSupport::parsePlayerDb);
    }

    static CompletableFuture<ResolvedProfile> profileByUuid(UUID uuid) {
        if (uuid == null || uuid.version() != 4) return CompletableFuture.completedFuture(null);
        String compact = uuid.toString().replace("-", "");
        URI uri = URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + compact);
        return profileRequest("uuid:" + compact, uri, LegacyDenickSupport::parseProfile)
                .thenApply(profile -> profile != null && profile.uuid.equals(uuid) ? profile : null);
    }

    static String keyFingerprint(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) return "";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(apiKey.trim().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    static CompletableFuture<BordicMatch> findBordicMatch(String apiKey, String packageId,
                                                           int finals, int beds, String woodType, Integer star) {
        if (apiKey == null || apiKey.isBlank()) return CompletableFuture.completedFuture(null);
        return superstarRequest(apiKey).thenApply(players -> findBestMatch(players, packageId, finals, beds, woodType, star));
    }

    static KillMatch detectKillMessage(String plainMessage) {
        if (plainMessage == null || plainMessage.isEmpty()) return null;
        Integer observedFinals = extractCount(FINALS_IN_MSG, plainMessage);
        Integer observedBeds = extractCount(BEDS_IN_MSG, plainMessage);
        for (KillEntry entry : KILL_ENTRIES) {
            Matcher matcher = entry.pattern.matcher(plainMessage);
            if (matcher.matches()) {
                return new KillMatch(entry.id, matcher.group(1), observedFinals, observedBeds);
            }
        }
        return null;
    }

    static Integer extractStar(String stripped, String username) {
        if (stripped == null || username == null || username.isEmpty()) return null;
        int colon = stripped.lastIndexOf(':');
        if (colon <= 0) return null;
        String beforeColon = stripped.substring(0, colon).trim();
        if (beforeColon.isEmpty()) return null;
        int nameIndex = indexOfUsername(beforeColon, username);
        if (nameIndex < 0) return null;
        String prefix = beforeColon.substring(0, nameIndex).trim();
        if (prefix.isEmpty()) return null;
        Integer star = null;
        Matcher matcher = STAR_BRACKET.matcher(prefix);
        while (matcher.find()) star = parseInt(matcher.group(1));
        return star;
    }

    private static void register(String id, String display, String... regexes) {
        for (String regex : regexes) KILL_ENTRIES.add(new KillEntry(id, Pattern.compile(regex)));
    }

    private static CompletableFuture<ResolvedProfile> profileRequest(String cacheKey, URI uri, ProfileParser parser) {
        long now = System.currentTimeMillis();
        while (true) {
            ProfileRequest current = PROFILE_CACHE.get(cacheKey);
            if (current != null && current.expiresAt > now) return current.future;
            ProfileRequest created = new ProfileRequest(new CompletableFuture<>(), now + TimeUnit.SECONDS.toMillis(20));
            boolean installed = current == null
                    ? PROFILE_CACHE.putIfAbsent(cacheKey, created) == null
                    : PROFILE_CACHE.replace(cacheKey, current, created);
            if (!installed) continue;
            trimProfileCache(now);
            try {
                WORKERS.execute(() -> {
                    try {
                        HttpResponse<String> response = HTTP.send(request(uri), HttpResponse.BodyHandlers.ofString());
                        if (response.statusCode() == 404) {
                            LOGGER.debug("Legacy denick profile lookup found no profile at {}", uri.getHost());
                            PROFILE_CACHE.remove(cacheKey, created);
                            created.future.complete(null);
                            return;
                        }
                        if (response.statusCode() != 200) {
                            throw new IOException("HTTP " + response.statusCode());
                        }
                        ResolvedProfile profile = parser.parse(response.body());
                        if (profile == null) {
                            LOGGER.debug("Legacy denick profile lookup returned no valid profile at {}", uri.getHost());
                            PROFILE_CACHE.remove(cacheKey, created);
                        } else {
                            created.expiresAt = System.currentTimeMillis() + PROFILE_CACHE_MS;
                        }
                        created.future.complete(profile);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        PROFILE_CACHE.remove(cacheKey, created);
                        LOGGER.warn("Legacy denick profile lookup was interrupted at {}", uri.getHost());
                        created.future.completeExceptionally(interrupted);
                    } catch (IOException | RuntimeException failure) {
                        PROFILE_CACHE.remove(cacheKey, created);
                        LOGGER.warn("Legacy denick profile lookup failed at {}: {}", uri.getHost(), failure.toString());
                        created.future.completeExceptionally(failure);
                    }
                });
            } catch (RejectedExecutionException rejected) {
                PROFILE_CACHE.remove(cacheKey, created);
                LOGGER.warn("Legacy denick profile lookup was rejected because the bounded worker queue is full");
                created.future.completeExceptionally(rejected);
            }
            return created.future;
        }
    }

    private static void trimProfileCache(long now) {
        if (PROFILE_CACHE.size() <= 512) return;
        PROFILE_CACHE.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
        if (PROFILE_CACHE.size() > 512) {
            PROFILE_CACHE.entrySet().stream().filter(entry -> entry.getValue().future.isDone()).limit(64)
                    .map(Map.Entry::getKey).forEach(PROFILE_CACHE::remove);
        }
    }

    private static CompletableFuture<List<BordicPlayer>> superstarRequest(String apiKey) {
        String fingerprint = keyFingerprint(apiKey);
        long now = System.currentTimeMillis();
        while (true) {
            SuperstarRequest current = SUPERSTAR_CACHE.get(fingerprint);
            if (current != null && current.expiresAt > now) return current.future;
            SuperstarRequest created = new SuperstarRequest(new CompletableFuture<>(), now + TimeUnit.SECONDS.toMillis(30));
            boolean installed = current == null
                    ? SUPERSTAR_CACHE.putIfAbsent(fingerprint, created) == null
                    : SUPERSTAR_CACHE.replace(fingerprint, current, created);
            if (!installed) continue;
            trimSuperstarCache(now);
            URI uri = URI.create(SUPERSTAR_URL + "?key=" + URLEncoder.encode(apiKey.trim(), StandardCharsets.UTF_8));
            try {
                WORKERS.execute(() -> {
                    try {
                        HttpResponse<String> response = HTTP.send(request(uri), HttpResponse.BodyHandlers.ofString());
                        if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
                        List<BordicPlayer> players = parseSuperstar(response.body());
                        created.expiresAt = System.currentTimeMillis() + SUPERSTAR_CACHE_MS;
                        created.future.complete(players);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        SUPERSTAR_CACHE.remove(fingerprint, created);
                        LOGGER.warn("Bordic superstar lookup was interrupted");
                        created.future.completeExceptionally(interrupted);
                    } catch (IOException | RuntimeException failure) {
                        SUPERSTAR_CACHE.remove(fingerprint, created);
                        LOGGER.warn("Bordic superstar lookup failed ({})", failure.getClass().getSimpleName());
                        created.future.completeExceptionally(failure);
                    }
                });
            } catch (RejectedExecutionException rejected) {
                SUPERSTAR_CACHE.remove(fingerprint, created);
                LOGGER.warn("Bordic superstar lookup was rejected because the bounded worker queue is full");
                created.future.completeExceptionally(rejected);
            }
            return created.future;
        }
    }

    private static void trimSuperstarCache(long now) {
        if (SUPERSTAR_CACHE.size() <= 8) return;
        SUPERSTAR_CACHE.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
        if (SUPERSTAR_CACHE.size() > 8) {
            SUPERSTAR_CACHE.entrySet().stream().filter(entry -> entry.getValue().future.isDone()).limit(4)
                    .map(Map.Entry::getKey).forEach(SUPERSTAR_CACHE::remove);
        }
    }

    private static List<BordicPlayer> parseSuperstar(String body) throws IOException {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(body);
        } catch (RuntimeException failure) {
            throw new IOException("invalid JSON response", failure);
        }
        if (!parsed.isJsonObject()) throw new IOException("invalid response object");
        JsonObject root = parsed.getAsJsonObject();
        if (!bool(root, "success")) throw new IOException("Bordic did not report success");
        JsonArray data = root.has("data") && root.get("data").isJsonArray() ? root.getAsJsonArray("data") : null;
        if (data == null) throw new IOException("Bordic response omitted data");
        List<BordicPlayer> players = new ArrayList<>(data.size());
        for (JsonElement element : data) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            String uuid = text(item, "uuid");
            String name = text(item, "name");
            if (parseUuid(uuid) == null || !validName(name)) continue;
            players.add(new BordicPlayer(uuid.replace("-", ""), name, integer(item, "star"),
                    integer(item, "finals"), integer(item, "beds"), text(item, "killMessage"), text(item, "activeWoodType")));
        }
        return List.copyOf(players);
    }

    private static BordicMatch findBestMatch(List<BordicPlayer> players, String packageId,
                                              int finals, int beds, String woodType, Integer star) {
        if (players == null || players.isEmpty()) return null;
        List<String> killMessages = killMessagesForPackage(packageId);
        if (killMessages.isEmpty()) return null;
        BordicPlayer best = null;
        long bestScore = Long.MAX_VALUE;
        int nearbyCount = 0;
        int bestCount = 0;
        for (BordicPlayer player : players) {
            if (player.killMessage == null || !killMessages.contains(player.killMessage)
                    || !woodTypeMatches(woodType, player.activeWoodType)
                    || star != null && star > 0 && player.star != star) continue;
            long finalsDistance = Math.abs((long) player.finals - finals);
            long bedsDistance = Math.abs((long) player.beds - beds);
            if (finalsDistance > 200L || bedsDistance > 200L) continue;
            nearbyCount++;
            long score = finalsDistance + bedsDistance;
            if (score < bestScore) {
                best = player;
                bestScore = score;
                bestCount = 1;
            } else if (score == bestScore) {
                bestCount++;
            }
        }
        return best == null ? null : new BordicMatch(best, bestScore, nearbyCount, nearbyCount >= 7 || bestCount != 1);
    }

    private static List<String> killMessagesForPackage(String packageId) {
        if (packageId == null || packageId.isEmpty()) return List.of();
        return switch (packageId) {
            case "stat" -> List.of("killmessages_counter", "killmessages_noble", "killmessages_glorious");
            case "oxd" -> List.of("killmessages_oxed");
            case "literally_spooky" -> List.of("killmessages_spooky");
            case "santas_workshop" -> List.of("killmessages_santa_workshop");
            case "social_distance" -> List.of("killmessages_social_distancing");
            case "triumph" -> List.of("killmessages_counter");
            default -> List.of("killmessages_" + packageId);
        };
    }

    private static boolean woodTypeMatches(String observed, String entryWood) {
        if (observed == null || observed.isEmpty() || entryWood == null || entryWood.isEmpty()) return true;
        return entryWood.startsWith("random_") || observed.equals(entryWood);
    }

    private static ResolvedProfile parseProfile(String body) throws IOException {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(body);
        } catch (RuntimeException failure) {
            throw new IOException("invalid JSON response", failure);
        }
        if (!parsed.isJsonObject()) return null;
        JsonObject root = parsed.getAsJsonObject();
        UUID uuid = parseUuid(text(root, "id"));
        String name = text(root, "name");
        if (uuid == null || uuid.version() != 4 || !validName(name)) return null;
        return new ResolvedProfile(uuid, name);
    }
    private static ResolvedProfile parsePlayerDb(String body) throws IOException {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(body);
        } catch (RuntimeException failure) {
            throw new IOException("invalid PlayerDB JSON response", failure);
        }
        if (!parsed.isJsonObject()) return null;
        JsonObject root = parsed.getAsJsonObject();
        if (!bool(root, "success")) return null;
        JsonObject player = object(object(root, "data"), "player");
        UUID uuid = parseUuid(text(player, "raw_id"));
        String name = text(player, "username");
        if (uuid == null || uuid.version() != 4 || !validName(name)) return null;
        return new ResolvedProfile(uuid, name);
    }

    private static HttpRequest request(URI uri) {
        return HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .header("Accept", "application/json").GET().build();
    }

    private static Integer extractCount(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? parseInt(matcher.group(1)) : null;
    }

    private static Integer parseInt(String value) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static int integer(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) return 0;
        try { return Math.max(0, value.getAsInt()); }
        catch (RuntimeException ignored) { return 0; }
    }

    private static boolean bool(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) return false;
        try { return value.getAsBoolean(); }
        catch (RuntimeException ignored) { return false; }
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : null;
    }

    private static String text(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) return null;
        try { return object.get(key).getAsString(); }
        catch (RuntimeException ignored) { return null; }
    }

    private static boolean validName(String name) {
        return name != null && PLAYER_NAME.matcher(name).matches();
    }

    static UUID parseUuid(String encoded) {
        if (encoded == null) return null;
        String compact = encoded.replace("-", "");
        if (!compact.matches("(?i)[0-9a-f]{32}")) return null;
        try {
            return UUID.fromString(compact.substring(0, 8) + "-" + compact.substring(8, 12) + "-"
                    + compact.substring(12, 16) + "-" + compact.substring(16, 20) + "-" + compact.substring(20));
        } catch (IllegalArgumentException ignored) { return null; }
    }

    private static int indexOfUsername(String beforeColon, String username) {
        if (beforeColon.endsWith(username)) return beforeColon.length() - username.length();
        String lower = beforeColon.toLowerCase(Locale.ROOT);
        if (lower.endsWith(username.toLowerCase(Locale.ROOT))) return beforeColon.length() - username.length();
        int space = beforeColon.lastIndexOf(' ');
        return space >= 0 && beforeColon.substring(space + 1).equalsIgnoreCase(username) ? space + 1 : -1;
    }

    private static ThreadFactory daemonThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "Lazify denick worker");
            thread.setDaemon(true);
            return thread;
        };
    }

    @FunctionalInterface
    private interface ProfileParser {
        ResolvedProfile parse(String body) throws IOException;
    }
    record ResolvedProfile(UUID uuid, String name) {}
    record KillMatch(String packageId, String killer, Integer observedFinals, Integer observedBeds) {}
    record BordicMatch(BordicPlayer player, long score, int nearbyCount, boolean ambiguous) {}

    record BordicPlayer(String uuid, String name, int star, int finals, int beds,
                        String killMessage, String activeWoodType) {}

    private record KillEntry(String id, Pattern pattern) {}

    private static final class ProfileRequest {
        private final CompletableFuture<ResolvedProfile> future;
        private volatile long expiresAt;
        private ProfileRequest(CompletableFuture<ResolvedProfile> future, long expiresAt) {
            this.future = future;
            this.expiresAt = expiresAt;
        }
    }

    private static final class SuperstarRequest {
        private final CompletableFuture<List<BordicPlayer>> future;
        private volatile long expiresAt;
        private SuperstarRequest(CompletableFuture<List<BordicPlayer>> future, long expiresAt) {
            this.future = future;
            this.expiresAt = expiresAt;
        }
    }
}
