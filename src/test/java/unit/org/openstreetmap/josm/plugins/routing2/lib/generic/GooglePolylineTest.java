// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.routing2.lib.generic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openstreetmap.josm.tools.Logging;

class GooglePolylineTest {
    static Stream<Arguments> values() {
        return Stream.of(Arguments.of((Object) new double[] { 38.5, -120.2 }),
                Arguments.of((Object) new double[] { 38.5, -120.2, 40.7, -120.95, 43.252, -126.453 }));
    }

    @Test
    void testEncode() {
        assertEquals("_p~iF~ps|U_ulLnnqC_mqNvxq`@",
                GooglePolyline.encode(38.5, -120.2, 40.7, -120.95, 43.252, -126.453));
    }

    @ParameterizedTest
    @MethodSource("values")
    void testDecode(double[] coordinates) {
        assertArrayEquals(coordinates, GooglePolyline.decode(GooglePolyline.encode(coordinates)));
    }

    /**
     * Just to debug shape points
     * @param args Not read
     */
    public static void main(String... args) throws IOException {
        final double[] shape1 = GooglePolyline.decode(
                "sobpiAdayzmEp@C~GiAPsK`JG?tJ@zN?h@?dJ{GHaA?sd@Ja{@Lk@?mN@oVE}WG{TEy]?e_@?q]A_E?g`@B_e@eA}BOcGq@kGiAsCi@iE_A}DeAcD_AkDqAmEoBeHsCoGkDuFcD_HyEgIaH_}@iw@cZyXeEyC}DwBgFwBcGcBaFm@oFK_FXcEn@qEvAmEjBoJvEmHbJ_R`YYb@q@bAqDzF|CfEfOpShE~Frd@pm@rXta@xDjGhDtGtAnC~HvP~IxUjHnU`FzRhHd^`DnSfLjy@jaAbvGpIjk@Hj@lAdIt`@fmCjPphA`I~h@dAfHtJto@rPvgANbAbh@rhD~Jzq@hF|[hSvkAbq@xjDtEvXdMpv@fIlk@bFx\\jIti@jF~\\hGjd@`Kfq@x]r_Cv@lFpb@ttCzBfUxAtQx@pPd@jRT`SHrXLpg@VpfA@fA@jF?lF?~CVdTZtUd@dLv@hNdAxLxB~OfGx_@zHbWrEbNbElJnDdIxCdGn@pA|AxCtBxD~Pp[tDtHvL`VjKlXxF~PdBbGvD|PhFb\\zLdz@|Ktw@n[l|BnEz[vO|kAvt@lkFtQxrA~Kfr@|OngA|]veCba@duCtGnd@fTj}AjJxx@tEvh@pBrXpAxQnDtf@fIbiAbAfN`Cj]bE~f@bFve@hFxb@dOjdAfOrdAxJrq@lR~pAjEzYz@nFxI`j@tA`JlBnMtD|VjRfpAdM`{@|Ml}@~o@xlEfMd{@\\vBz@vFtAfH`@rBjD~NzAtFhEhMft@ztBtGpSxD`OrCtN~BnNpAvLfAtMj@`NLvLF`KE|hA?tH?jEAdzA?fG?pFAxvA?jA?zFAhFCbPEnb@I`o@A`G@lFDdyA?jF?fGBjwA?r@?`G?bGKxxA?tF_E?g@?{U?{CAaQCY?kEAsD?Y?oVAwOAeFAS?gF?yE?Y@}S@?zF?d[xHC",
                1e6);
        final StringBuilder sb = new StringBuilder("http://127.0.0.1:8111/add_way?way=");
        for (int i = 0; i < shape1.length; i += 2) {
            sb.append(shape1[i]).append(',').append(shape1[i + 1]);
            if (i + 2 < shape1.length) {
                sb.append(';');
            }
        }
        var con = URI.create(sb.toString()).toURL().openConnection();
        con.connect();
        Logging.error(Objects.toString(con.getContent()));
    }
}