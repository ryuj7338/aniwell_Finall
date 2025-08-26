package com.example.RSW.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.RSW.repository.MemberRepository;
import com.example.RSW.util.Ut;
import com.example.RSW.vo.Member;
import com.example.RSW.vo.ResultData;


import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class MemberService {

    @Value("${custom.siteMainUri}")
    private String siteMainUri;

    @Value("${custom.siteName}")
    private String siteName;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MailService mailService;

    @Autowired(required = false)
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private FirebaseAuth firebaseAuth;


    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    // 로그인 비교용(BCrypt)
    public boolean matchesRawPw(String raw, String encoded) {
        return passwordEncoder.matches(raw, encoded);
    }

    public ResultData notifyTempLoginPwByEmail(Member actor) {
        String title = "[" + siteName + "] 임시 패스워드 발송";
        String tempPassword = Ut.getTempPassword(6);
        String body = "<h1>임시 패스워드 : " + tempPassword + "</h1>";
        body += "<a href=\"" + siteMainUri + "/usr/member/login\" target=\"_blank\">로그인 하러가기</a>";

        ResultData sendResultData = mailService.send(actor.getEmail(), title, body);

        if (sendResultData.isFail()) {
            return sendResultData;
        }

        setTempPassword(actor, tempPassword);

        return ResultData.from("S-1", "계정의 이메일주소로 임시 패스워드가 발송되었습니다.");
    }

    private void setTempPassword(Member actor, String tempPassword) {
        String encodedPw = Ut.sha256(tempPassword);
        memberRepository.modify(actor.getId(), encodedPw, null, null, null, null, null, null);
    }

    public ResultData<Integer> join(String loginId, String loginPw, String name, String nickname, String cellphone,
                                    String email, String address, String authName, int authLevel) {

        // 아이디 중복
        if (getMemberByLoginId(loginId) != null) {
            return ResultData.from("F-7", Ut.f("이미 사용중인 아이디(%s)입니다", loginId));
        }

        // 이메일 중복
        if (getMemberByEmail(email) != null) {
            return ResultData.from("F-9", "이미 사용 중인 이메일입니다.");
        }

        // 닉네임 중복
        if (getMemberByNickname(nickname) != null) {
            return ResultData.from("F-10", "이미 사용 중인 닉네임입니다.");
        }

        // 전화번호 숫자만 추출 + 중복
        String digits = cellphone == null ? "" : cellphone.replaceAll("\\D", "");
        if (getMemberByCellphone(digits) != null) {
            return ResultData.from("F-11", "이미 사용 중인 전화번호입니다.");
        }

        // 이름과 이메일 중복 체크
        if (getMemberByNameAndEmail(name, email) != null) {
            return ResultData.from("F-8", Ut.f("이미 사용중인 이름(%s)과 이메일(%s)입니다", name, email));
        }

        // ✅ 비밀번호 암호화: SHA-256로 통일
        String encodedPw = (loginPw != null && loginPw.matches("^[0-9a-fA-F]{64}$"))
                ? loginPw
                : Ut.sha256(loginPw);

        memberRepository.doJoin(loginId, encodedPw, name, nickname, digits, email, address, authName, authLevel);

        // 최근 삽입된 회원 ID 조회
        int id = memberRepository.getLastInsertId();

        // 성공적으로 회원가입된 후 반환
        return ResultData.from("S-1", "회원가입 성공", "가입 성공 id", id);
    }

    public Member getMemberByNameAndEmail(String name, String email) {

        return memberRepository.getMemberByNameAndEmail(name, email);

    }

    public Member getMemberByLoginId(String loginId) {

        return memberRepository.getMemberByLoginId(loginId);
    }

    public Member getMemberById(int id) {
        return memberRepository.getMemberById(id);
    }

    public ResultData modify(int loginedMemberId, String loginPw, String name, String nickname, String cellphone,
                             String email, String photo, String address) {

        String encoded = null;
        if (loginPw != null && !loginPw.trim().isEmpty()) {
            encoded = loginPw.matches("^[0-9a-fA-F]{64}$") ? loginPw : Ut.sha256(loginPw);
        }

        String digits = cellphone == null ? null : cellphone.replaceAll("\\D", "");

        memberRepository.modify(loginedMemberId, encoded, name, nickname, digits, email, photo, address);

        return ResultData.from("S-1", "회원정보 수정 완료");
    }


    public ResultData modifyWithoutPw(int loginedMemberId, String name, String nickname, String cellphone,
                                      String email, String photo, String address) {

        String digits = cellphone == null ? null : cellphone.replaceAll("\\D", "");

        memberRepository.modifyWithoutPw(loginedMemberId, name, nickname, digits, email, photo, address);

        return ResultData.from("S-1", "회원정보 수정 완료");
    }


    public ResultData withdrawMember(int id) {
        memberRepository.withdraw(id);
        return ResultData.from("S-1", "탈퇴 처리 완료");
    }


    public void updateAuthLevel(int memberId, int authLevel) {
        memberRepository.updateAuthLevel(memberId, authLevel);
    }

    public List<Member> getForPrintMembers(String searchType, String searchKeyword) {
        return memberRepository.getForPrintMembersWithCert(searchType, searchKeyword);
    }


    public void updateVetCertInfo(int memberId, String fileName, int approved) {
        memberRepository.updateVetCertInfo(memberId, fileName, approved);
    }

    public int countByAuthLevel(int level) {
        return memberRepository.countByAuthLevel(level);
    }

    // 관리자 목록을 가져오는 메서드
    public List<Member> getAdmins() {
        return memberRepository.findByAuthLevel(7); // 관리자 권한이 7인 회원들
    }

    // ✅ 소셜 로그인 시 기존 회원 조회 or 신규 생성
    public Member getOrCreateSocialMember(String provider, String socialId, String email, String name) {
        Member member = memberRepository.getMemberBySocial(provider, socialId);
        if (member != null) return member;

        String loginId = email != null ? email : provider + "_" + socialId;
        String nickname = name != null ? name : "소셜회원";
        String loginPw = Ut.sha256("SOCIAL_LOGIN");

        // ✅ 전용 insert 사용
        memberRepository.doJoinBySocial(
                loginId,
                loginPw,
                provider,
                socialId,
                name,
                nickname,
                email
        );

        return memberRepository.getMemberBySocial(provider, socialId);
    }


    // ✅ 이메일 기반 소셜 가입
    public Member getOrCreateByEmail(String email, String name, String provider) {
        Member member = memberRepository.findByEmail(email);
        if (member == null) {
            String loginId = provider + "_" + email.split("@")[0];
            String loginPw = Ut.sha256("temp_pw_" + provider);

            memberRepository.doJoinBySocial(
                    loginId,
                    loginPw,
                    provider,
                    provider + "_" + email,
                    name,
                    name,
                    email
            );
            member = memberRepository.findByEmail(email);
        }
        return member;
    }

    // ✅ Firebase 커스텀 토큰 생성
    public String createFirebaseCustomToken(String uid) {
        String redisKey = "firebaseToken::" + uid;

        if (redisTemplate != null) {
            try {
                String cachedToken = redisTemplate.opsForValue().get(redisKey);
                if (cachedToken != null) return cachedToken;
            } catch (RedisConnectionFailureException e) {
                log.warn("Redis 꺼져 있음 - 토큰 캐시 무시");
            }
        }

        try {
            String token = firebaseAuth.createCustomToken(uid);

            if (redisTemplate != null) {
                try {
                    redisTemplate.opsForValue().set(redisKey, token, 1, TimeUnit.HOURS);
                } catch (RedisConnectionFailureException e) {
                    log.warn("Redis 꺼져 있음 - 캐시 실패: {}", e.getMessage());
                }
            }

            return token;
        } catch (FirebaseAuthException e) {
            throw new RuntimeException("❌ Firebase 토큰 생성 실패: " + e.getMessage());
        }
    }

    public Member findByEmail(String email) {
        return memberRepository.findByEmail(email);
    }

    public String getOrCreateFirebaseToken(Member member) {
        String redisKey = "firebase:token:" + member.getUid();
        String lockKey = redisKey + ":lock";

        if (redisTemplate != null) {
            try {
                String cachedToken = redisTemplate.opsForValue().get(redisKey);
                if (cachedToken != null && cachedToken.split("\\.").length - 1 == 2) {
                    return cachedToken;
                }
                redisTemplate.delete(redisKey);
            } catch (RedisConnectionFailureException e) {
                log.warn("Redis 꺼짐 - 캐시 조회 실패");
            }
        }

        Boolean isLockAcquired = null;
        if (redisTemplate != null) {
            try {
                isLockAcquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 5, TimeUnit.SECONDS);
            } catch (RedisConnectionFailureException e) {
                log.warn("Redis 꺼짐 - 락 사용 안 함");
            }
        }

        if (Boolean.FALSE.equals(isLockAcquired)) {
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {
            }
            if (redisTemplate != null) {
                try {
                    return redisTemplate.opsForValue().get(redisKey);
                } catch (RedisConnectionFailureException e) {
                    log.warn("Redis 꺼짐 - 재조회 실패");
                    return null;
                }
            }
        }

        try {
            String customToken = firebaseAuth.createCustomToken(member.getUid());
            if (redisTemplate != null) {
                try {
                    redisTemplate.opsForValue().set(redisKey, customToken, 12, TimeUnit.HOURS);
                } catch (RedisConnectionFailureException e) {
                    log.warn("Redis 꺼짐 - 캐시 저장 실패");
                }
            }
            return customToken;
        } catch (FirebaseAuthException e) {
            throw new RuntimeException("Firebase 토큰 생성 실패: " + e.getMessage());
        } finally {
            if (redisTemplate != null) {
                try {
                    redisTemplate.delete(lockKey);
                } catch (RedisConnectionFailureException e) {
                    log.warn("Redis 꺼짐 - 락 해제 실패");
                }
            }
        }
    }

    public Member findByUid(String uid) {
        Member member = memberRepository.findByUid(uid);
        if (member == null && uid != null) {
            String provider = uid.contains("_") ? uid.split("_")[0] : "google";
            String socialId = uid.contains("_") ? uid.split("_")[1] : uid;
            member = getOrCreateSocialMember(provider, socialId, null, "신규사용자");
        }
        return member;
    }

    public Member findCachedMemberOrDb(String uid) {
        String redisKey = "firebase:member:" + uid;

        if (redisTemplate != null) {
            try {
                String cachedId = redisTemplate.opsForValue().get(redisKey);
                if (cachedId != null) {
                    return getMemberById(Integer.parseInt(cachedId));
                }
            } catch (RedisConnectionFailureException e) {
                log.warn("Redis 꺼짐 - UID 캐시 조회 실패");
            }
        }

        Member member = findByUid(uid);

        if (member != null && redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(redisKey, String.valueOf(member.getId()), 24, TimeUnit.HOURS);
            } catch (RedisConnectionFailureException e) {
                log.warn("Redis 꺼짐 - UID 캐시 저장 실패");
            }
        }

        return member;
    }

    public Member getMemberByNickname(String nickname) {
        return memberRepository.getMemberByNickname(nickname);
    }

    public Member getMemberByEmail(String email) {
        return memberRepository.getMemberByEmail(email);
    }

    public Member getMemberByCellphone(String cellphone) {
        return memberRepository.getMemberByCellphone(cellphone);
    }

}
